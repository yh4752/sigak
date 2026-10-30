# Single-server Sigak deployment

## Goal and scope

Restore access to the existing real application and prepare a repeatable deployment of the same React → Spring Boot → PostgreSQL/AI/search architecture. No hosting account or domain exists. Paid provisioning and public DNS/TLS activation remain dependent on a selected account and hostname; do not report a local URL as an Internet deployment.

## Approach

A split frontend/API deployment adds a second origin and a separately managed API host. A single server running Compose preserves the existing system and is the selected approach. A static Sites-only publication cannot run the JVM backend; it would not satisfy real article search and persistence. Do not replace this application with a static imitation.

Use Caddy as the sole published service. It serves the compiled React SPA and proxies the exact public read endpoints to Spring Boot. Default local binding is 127.0.0.1:8088. The server operator can change the address/ports and provide a domain for Caddy automatic HTTPS after DNS and firewall setup. PostgreSQL, AI, Elasticsearch, Qdrant and Neo4j have no published host ports in the deployment stack. Persistent deployment volumes are distinct from existing local development volumes.

The backend image packages the executable jar and existing research artifacts. No product code or API contract changes are required. The frontend image builds with an empty VITE_API_BASE_URL so it uses the current origin. Proxy routes allow GET/HEAD only for `/api/articles`, numeric article detail and graph context, and `/api/research/overview`. Internal/unknown API requests and write methods are denied. Credentials are required environment inputs; the example contains no real secret. Do not print a resolved production Compose configuration containing credentials.

## Acceptance

- Existing backend/frontend/AI/evaluation gates are freshly executed and recorded.
- Deployment configuration validates and refuses missing database/Neo4j credentials.
- Images build from source; the actual composed application returns articles, search, detail and research data through the same address.
- Browser reloads of article/research routes return the SPA, while internal API paths, unsupported methods and hidden dotfiles are denied.
- Record observed readiness/counts/search mode and Docker image/build limits honestly.
- Keep the RAG dataset human review gate unchanged. Keep original untracked documentation intact.
- Audit local versus remote Git state; remote publication must not overwrite main or lose local work.

## Temporary preview follow-up

Since no hosting account exists, a temporary Quick Tunnel can expose only the validated Caddy web edge through the deployment network. This does not publish private service ports, requires no paid resource, and preserves public API restrictions. It is a development/demo link with no uptime guarantee and terminates when the computer/tunnel stops. Permanent single-server hosting remains a separate follow-up.
