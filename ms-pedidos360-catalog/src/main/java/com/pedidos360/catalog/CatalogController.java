package com.pedidos360.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
  private final ProductRepository products;
  private final ReservationRepository reservations;
  private final ObjectMapper json;

  public CatalogController(ProductRepository p, ReservationRepository r, ObjectMapper j) {
    products = p;
    reservations = r;
    json = j;
  }

  public record ProductInput(
      @NotBlank @Size(max = 80) String name,
      @NotBlank @Size(max = 80) String category,
      @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal price,
      @NotNull @Min(1) @Max(20) Long storeId,
      @NotNull @PositiveOrZero Integer stock,
      Boolean active) {}

  public record Line(
      @NotNull @Positive Long productId, @NotNull @Min(1) @Max(10000) Integer quantity) {}

  public record Reserve(
      @NotNull @Min(1) @Max(20) Long storeId,
      @NotEmpty @Size(max = 100) List<@NotNull @Valid Line> items) {}

  @GetMapping("/products")
  @PreAuthorize("hasAnyRole('Admin','Operador','Cliente')")
  public List<Product> list(@RequestParam(defaultValue = "1") Long storeId) {
    return products.findByStoreIdAndActiveTrueOrderByName(storeId);
  }

  @GetMapping("/products/{id}")
  @PreAuthorize("hasAnyRole('Admin','Operador','Cliente')")
  public Product get(@PathVariable Long id) {
    return products
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto inexistente"));
  }

  @PostMapping("/products")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('Admin')")
  @Transactional
  public Product create(@Valid @RequestBody ProductInput r) {
    Product p = new Product();
    apply(p, r);
    return products.save(p);
  }

  @PutMapping("/products/{id}")
  @PreAuthorize("hasRole('Admin')")
  @Transactional
  public Product update(@PathVariable Long id, @Valid @RequestBody ProductInput r) {
    Product p =
        products.lock(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    apply(p, r);
    return products.save(p);
  }

  @DeleteMapping("/products/{id}")
  @PreAuthorize("hasRole('Admin')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void deactivate(@PathVariable Long id) {
    Product p = get(id);
    p.active = false;
    products.save(p);
  }

  private void apply(Product p, ProductInput r) {
    p.name = r.name();
    p.category = r.category();
    p.price = r.price();
    p.storeId = r.storeId();
    p.stock = r.stock();
    p.active = r.active() == null || r.active();
  }

  // These endpoints additionally require the backend-only shared credential. A user bearer alone
  // cannot mutate stock.
  @PutMapping("/reservations/{id}")
  @PreAuthorize("hasRole('SERVICE')")
  @Transactional
  public Map<String, Object> reserve(
      @PathVariable String id,
      @Valid @RequestBody Reserve r,
      @RequestHeader("X-Service-Key") String key) {
    serviceKey(key);
    if (!id.matches("order-[0-9]+(-[a-fA-F0-9-]{36})?"))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Identificador de reserva inválido");
    TreeMap<Long, Integer> totals = new TreeMap<>();
    r.items().forEach(i -> totals.merge(i.productId(), i.quantity(), Math::addExact));
    var first = reservations.lock(id);
    // Always lock products in the same order. Duplicate requests serialize before checking the
    // reservation.
    List<Product> locked =
        totals.keySet().stream()
            .map(
                x ->
                    products
                        .lock(x)
                        .orElseThrow(
                            () ->
                                new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "Producto inexistente")))
            .toList();
    String payload = encode(r), digest = digest(payload);
    var existing = reservations.lock(id);
    if (existing.isPresent()) {
      var old = existing.get();
      if (old.released)
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Intento de reserva liberado; se requiere un nuevo identificador");
      if (!old.digest.equals(digest))
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserva incompatible");
      return Map.of("id", id, "reserved", true);
    }
    for (Product p : locked) {
      int qty = totals.get(p.id);
      if (!p.active || !p.storeId.equals(r.storeId()) || p.stock < qty)
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Stock insuficiente o producto de otro local");
      p.stock -= qty;
    }
    var reservation = new Reservation();
    reservation.id = id;
    reservation.payload = payload;
    reservation.digest = digest;
    reservations.save(reservation);
    return Map.of("id", id, "reserved", true);
  }

  @DeleteMapping("/reservations/{id}")
  @PreAuthorize("hasRole('SERVICE')")
  @Transactional
  public Map<String, Object> release(
      @PathVariable String id, @RequestHeader("X-Service-Key") String key) {
    serviceKey(key);
    var found = reservations.lock(id);
    if (found.isEmpty()) {
      var tombstone = new Reservation();
      tombstone.id = id;
      tombstone.digest = "released";
      tombstone.payload = "{}";
      tombstone.released = true;
      reservations.saveAndFlush(tombstone);
      return Map.of("id", id, "released", true);
    }
    if (found.get().released) return Map.of("id", id, "released", true);
    Reserve r = decode(found.get().payload);
    TreeMap<Long, Integer> totals = new TreeMap<>();
    r.items().forEach(i -> totals.merge(i.productId(), i.quantity(), Math::addExact));
    List<Product> locked =
        totals.keySet().stream().map(x -> products.lock(x).orElseThrow()).toList();
    Reservation saved = reservations.lock(id).orElseThrow();
    if (!saved.released) {
      locked.forEach(p -> p.stock = Math.addExact(p.stock, totals.get(p.id)));
      saved.released = true;
    }
    return Map.of("id", id, "released", true);
  }

  @org.springframework.beans.factory.annotation.Value("${INTERNAL_SERVICE_KEY}")
  private String expectedKey;

  private void serviceKey(String key) {
    if (expectedKey == null
        || expectedKey.length() < 32
        || !MessageDigest.isEqual(
            expectedKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8)))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Servicio no autorizado");
  }

  private String encode(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Reserve decode(String s) {
    try {
      return json.readValue(s, Reserve.class);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String digest(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
