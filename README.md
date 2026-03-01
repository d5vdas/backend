# Run Run Run Backend (Strava-like MVP)

Java Spring Boot backend for:
- Auth (register/login + JWT)
- Activity tracking (start, points, stop, list/detail)
- Social basics (follow/unfollow, like/unlike, comment/delete, counts)

## Run

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8083
```

Health:

```bash
curl http://localhost:8083/health
```

## Core APIs

### Auth
- `POST /auth/register`
- `POST /auth/login`

### Profile
- `GET /users/me` (Bearer token)

### Activities
- `POST /activities/start`
- `POST /activities/{id}/points`
- `POST /activities/{id}/stop`
- `GET /activities/me`
- `GET /activities/{id}`

### Social
- `POST /social/follow/{userId}`
- `DELETE /social/follow/{userId}`
- `POST /social/activities/{activityId}/like`
- `DELETE /social/activities/{activityId}/like`
- `POST /social/activities/{activityId}/comment`
- `DELETE /social/comments/{commentId}`
- `GET /social/activities/{activityId}/counts`
- `GET /social/me/follows/counts`

## One-command demo

```bash
bash scripts/demo_flow.sh
```

This script does:
1. register + login
2. start activity
3. add points
4. stop activity
5. list/detail
6. social counts

## Proper public deployment (Render)

This repo is now prepared for Render deployment with:
- `Dockerfile`
- `render.yaml`
- env-based config in `application.yml`

### Steps
1. Push this project to GitHub.
2. Go to https://render.com and sign in.
3. Create **Blueprint** deploy and select your GitHub repo.
4. Render will read `render.yaml` and create:
   - Web service: `runrunrun-backend`
   - Postgres DB: `runrunrun-db`
5. Wait for deploy to finish.
6. Open your Render service URL and test:

```bash
curl https://<your-render-url>/health
```

If it returns `OK`, share that base URL with friends.

### Notes
- Free tier may sleep after inactivity (first request can be slow).
