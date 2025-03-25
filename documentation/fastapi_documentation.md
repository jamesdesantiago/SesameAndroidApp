# 📘 FastAPI Project Documentation

## 🧠 Overview
This FastAPI backend serves a list-sharing application with Firebase authentication and PostgreSQL as the database. It allows users to create lists, add places, invite collaborators, follow other users, and manage their own profiles.

---

## 🚀 Main Technologies
- **FastAPI**: Web framework for building APIs
- **Firebase Admin SDK**: For verifying ID tokens
- **PostgreSQL**: Relational database
- **psycopg2**: PostgreSQL database adapter for Python
- **Pydantic**: Data validation
- **Dotenv**: For environment variable management
- **Logging**: Built-in Python logging for monitoring

---

## 🔐 Authentication
- JWT tokens from Firebase are passed in the `Authorization` header as `Bearer <token>`.
- Middleware logs request headers.
- Firebase token is verified in endpoints requiring authentication.

---

## 📦 Environment Variables
- `.env` must include:
    - `DB_PASSWORD`: Password for PostgreSQL connection

---

## 🧱 Database Tables Used
Tables referenced from the PostgreSQL schema:
- `users`
- `user_follows`
- `friend_requests`
- `lists`
- `list_collaborators`
- `places`

---

## 🧾 Models
- `User`, `UsernameSet`
- `ListCreate`, `ListUpdate`, `ListResponse`
- `PlaceItem`, `PlaceCreate`, `PlaceUpdate`
- `CollaboratorAdd`

---

## 🔄 Middleware
Logs all incoming HTTP request headers.

---

## 🧰 Utility Functions
- `get_db()`: Creates and returns a PostgreSQL connection.
- `get_owner_id(firebase_token, conn)`: Ensures user is registered in the DB using Firebase UID or email.
- `get_collaborators(list_id, conn)`: Gets emails of list collaborators.

---

## 📮 Endpoints

### 👤 Users
- `GET /users/check-username` → Check if user needs to set a username.
- `POST /users/set-username` → Set or update username.
- `GET /users/following` → List users the current user follows.
- `GET /users/followers` → List users who follow the current user.
- `GET /users/search?email=...` → Search users by email or username.
- `GET /users/{user_id}` → Get public user profile.
- `POST /users/{user_id}/follow` → Follow a user.
- `DELETE /users/{user_id}/follow` → Unfollow a user.
- `POST /users/{user_id}/friend-request` → Send a friend request.

### 📋 Lists
- `POST /lists` → Create a new list.
- `GET /lists` → Get lists owned by the user.
- `DELETE /lists/{list_id}` → Delete a user-owned list.
- `PATCH /lists/{list_id}` → Update list name or privacy.
- `GET /lists/{list_id}` → Get detailed list including places and collaborators.
- `POST /lists/{list_id}/collaborators` → Add a single collaborator.
- `POST /lists/{list_id}/collaborators/batch` → Add multiple collaborators.

### 📍 Places
- `POST /lists/{list_id}/places` → Add a new place to a list.
- `PATCH /lists/{list_id}/places/{place_id}` → Update place notes.

---

## 📑 Error Handling
- Validation errors return `422 Unprocessable Entity` with details.
- Firebase token verification errors return `401 Unauthorized`.
- Custom errors for invalid ratings, missing lists, or duplicate inserts.

---

## 📌 Notes
- `username` must be 1–30 characters, with only letters, numbers, `.` or `_`, no spaces.
- Collaborators can be added even if they don't have a Firebase account yet.
- Places have allowed `rating` values: "Must Visit", "Worth Visiting", "Not Worth Visiting".

---

## ✅ Future Improvements
- Add pagination and filtering to `/lists` and `/lists/{list_id}`.
- Implement friend request accept/reject flow.
- Unit and integration tests.

---

## 🧑‍💻 Author
FastAPI project built by James De Santiago.

