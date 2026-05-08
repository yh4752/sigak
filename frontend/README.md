# Frontend

[English](README.md) | [한국어](README.ko.md)

The Sigak frontend uses React, TypeScript, and Vite.

## Responsibilities
- Article list UI
- Article detail UI
- Search UI
- AI summary and insight display
- API client modules for backend communication
- Runtime response validation at the API boundary

## Current Status
The first frontend implementation includes a minimal home/search UI and article detail page backed by the Spring Boot article APIs.

The article detail page emphasizes summary, why-it-matters context, topics, source metadata, and related articles. It intentionally does not display the raw `importanceScore`; the score is currently used for ranking article lists.

## API Client Direction
- Use Axios for HTTP requests to the Spring Boot backend.
- Use Zod to validate backend API responses before UI components consume them.
- Keep API access under `src/api/` instead of calling the backend directly from components.
- Configure the backend base URL through `VITE_API_BASE_URL`, defaulting to `http://localhost:8080` for local development.

## Run Locally
Requirements:
- Node.js
- npm

From this directory:

```bash
npm install
npm run dev
```

The default Vite dev server URL is:

```txt
http://localhost:5173
```

## Test
From this directory:

```bash
npm test
```
