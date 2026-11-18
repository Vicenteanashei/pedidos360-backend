import json
import unittest
from pathlib import Path


class GatewayRoutesTest(unittest.TestCase):
    def test_ea3_routes_require_entra_jwt_and_route_to_the_bff(self):
        aws = Path(__file__).resolve().parents[1] / "infra" / "aws"
        expected = {
            "GET /api/kafka/cluster",
            "GET /api/kafka/topics",
            "GET /api/kafka/topics/{topic}/config",
            "POST /api/kafka/topics",
            "PUT /api/kafka/topics/{topic}/config",
            "DELETE /api/kafka/topics/{topic}/config/{property}",
            "DELETE /api/kafka/topics/{topic}",
            "GET /api/kafka/groups",
            "GET /api/kafka/groups/{group}",
            "POST /api/kafka/groups/{group}/offsets",
            "GET /api/audit",
            "GET /api/audit/timeline",
            "GET /api/report",
            "GET /api/report/kpis",
            "GET /api/report/top-products",
            "GET /api/reports",
            "GET /api/reports/kpis",
            "GET /api/reports/top-products",
            "GET /api/outbox/stream",
            "POST /api/outbox/stream/replay",
        }
        for name in ("web-sin-cloudfront-lab.json", "web-existente-lab.json"):
            with self.subTest(template=name):
                resources = json.loads((aws / name).read_text(encoding="utf-8"))["Resources"]
                routes = {}
                for resource in resources.values():
                    if resource["Type"] == "AWS::ApiGatewayV2::Route":
                        route = resource["Properties"]
                        key = route["RouteKey"]
                        self.assertNotIn(key, routes)
                        routes[key] = route

                self.assertFalse(expected - routes.keys())
                for key in expected:
                    with self.subTest(route=key):
                        route = routes[key]
                        self.assertEqual(route["ApiId"], {"Ref": "HttpApi"})
                        self.assertEqual(route["AuthorizationType"], "JWT")
                        self.assertEqual(route["AuthorizerId"], {"Ref": "EntraAuthorizer"})
                        self.assertIn("access_as_user", route["AuthorizationScopes"])
                        self.assertEqual(route["Target"], {
                            "Fn::Join": ["/", ["integrations", {"Ref": "BffIntegration"}]]
                        })
