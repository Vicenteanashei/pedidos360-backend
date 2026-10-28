# Pull Request Summary: AWS-Pedidos360 Project Overview

## Date: 23-28 Sept 2026

This PR contains the complete conversation history and implementation details for the Pedidos360 backend/frontend project deployment on AWS.

## Key Achievements

### Infrastructure Architecture (Two-Instance Separation)
- **ec2-apps (BFF)** - `i-0e959377ac54f9f78`, Elastic IP `34.199.241.248`, t3.medium
  - Runs only `ms-pedidos360-bff` on port 8080
  - Security Group: allows 8080 from public + 22 from specific IP
- **ec2-orders (Backend + DB)** - `i-0c14b6b5b4ebf3a33`, t3.medium  
  - Runs `ms-pedidos360-orders` on port 8081 + `pedidos360-oracle` (Oracle Free 23c)
  - Security Group: 8081 only accessible from BFF's Security Group, 22 from specific IP
- **Private network communication**: BFF → Orders via private IP `172.31.18.29:8081`
- **Oracle data persisted** via Docker volume `oradata` on EBS disk

### API Gateway Setup (us-east-1)
- API Gateway: `qjnygg9zh5.execute-api.us-east-1.amazonaws.com` (pedidos360-api)
- JWT Authorizer (Azure Entra ID): tenant `9d7e1df5-4b2b-4f57-944c-0ea679424efd`
- Routes configured with HTTP_PROXY integration to BFF:8080
- CORS OPTIONS routes configured (no auth required)
- Double JWT validation: Gateway + BFF

### Frontend Configuration
- `~/frontCloud/pedidos360-frontend` configured with `VITE_API_BASE_URL` pointing to API Gateway
- MSAL integration for Azure Entra ID authentication
- HTTP headers include `Authorization: Bearer <token>` and `Content-Type`
- Successfully tested end-to-end with 6 orders

### RabbitMQ Integration (Latest)
- New instance added for RabbitMQ messaging in same lab
- AWS credentials updated for RabbitMQ lab environment
- Integration pending verification

### Cleanup & Optimization
- Removed unused Docker images from each instance
- Cleaned up old resources (stopped us-west-2 instance, old API Gateway as backup)

### Testing & Verification
- Direct BFF access (8080) works with valid JWT → creates orders
- API Gateway access works with JWT validation
- Direct orders service access (8081) works from within instance without token (network-restricted)
- All 6 existing orders verified in Oracle database

## Key Learnings
- Security: 8081 only accessible via SG from BFF, not from public/internet
- Token validation: BFF validates JWT, orders service doesn't validate (internal traffic)
- Docker volumes preserve Oracle data across container restarts
- API Gateway provides additional auth layer but BFF also validates
