// Datos provisionales mientras no exista ms-pedidos360-catalog: despues se cargan desde /api/catalog/products
export const STORES = [
  { id: 1, name: 'Panadería La Espiga' },
  { id: 2, name: 'Café del Barrio' },
  { id: 3, name: 'Pastelería Dulce Hogar' },
];

export const PRODUCTS = [
  { id: 10, name: 'Marraqueta (kg)', price: 2200 },
  { id: 11, name: 'Pan amasado (6 un.)', price: 2500 },
  { id: 12, name: 'Hallulla (kg)', price: 2300 },
  { id: 20, name: 'Café americano', price: 1800 },
  { id: 21, name: 'Cappuccino', price: 2600 },
  { id: 30, name: 'Kuchen de nuez (trozo)', price: 2900 },
  { id: 31, name: 'Torta tres leches (entera)', price: 18990 },
];

export const productName = (id) => PRODUCTS.find((p) => p.id === id)?.name ?? `Producto #${id}`;
export const storeName = (id) => STORES.find((s) => s.id === id)?.name ?? `Local #${id}`;
