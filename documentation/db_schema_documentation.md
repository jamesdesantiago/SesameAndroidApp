# 📊 Database Schema Documentation

## 🔹 Schema: `public`

---

### 1. `friend_requests`

| Column Name | Data Type           | Constraints                                  |
|-------------|---------------------|----------------------------------------------|
| id          | integer             | PRIMARY KEY (`friend_requests_pkey`)         |
| sender_id   | integer             | FOREIGN KEY (`friend_requests_sender_id_fkey`) |
| receiver_id | integer             | FOREIGN KEY (`friend_requests_receiver_id_fkey`) |
| status      | character varying   |                                              |
| created_at  | timestamp           |                                              |
| sender_id + receiver_id |         | UNIQUE (`unique_request`)                   |

---

### 2. `list_collaborators`

| Column Name | Data Type           | Constraints                                      |
|-------------|---------------------|--------------------------------------------------|
| id          | integer             | PRIMARY KEY (`list_collaborators_pkey`)         |
| list_id     | integer             | FOREIGN KEY (`list_collaborators_list_id_fkey`) |
| user_id     | integer             | FOREIGN KEY (`list_collaborators_user_id_fkey`) |
| created_at  | timestamp           |                                                  |
| list_id + user_id |               | UNIQUE (`unique_collaborator`)                  |

---

### 3. `list_follows`

| Column Name | Data Type           | Constraints                                  |
|-------------|---------------------|----------------------------------------------|
| id          | integer             | PRIMARY KEY (`list_follows_pkey`)            |
| list_id     | integer             | FOREIGN KEY (`list_follows_list_id_fkey`)    |
| user_id     | integer             | FOREIGN KEY (`list_follows_user_id_fkey`)    |
| created_at  | timestamp           |                                              |
| list_id + user_id |               | UNIQUE (`unique_follow`)                     |

---

### 4. `list_shares`

| Column Name | Data Type           | Constraints                                  |
|-------------|---------------------|----------------------------------------------|
| id          | integer             | PRIMARY KEY (`list_shares_pkey`)             |
| list_id     | integer             | FOREIGN KEY (`list_shares_list_id_fkey`)     |
| user_id     | integer             | FOREIGN KEY (`list_shares_user_id_fkey`)     |
| created_at  | timestamp           |                                              |
| list_id + user_id |               | UNIQUE (`unique_share`)                      |

---

### 5. `lists`

| Column Name | Data Type           | Constraints                                 |
|-------------|---------------------|---------------------------------------------|
| id          | integer             | PRIMARY KEY (`lists_pkey`)                  |
| name        | character varying   | UNIQUE (`unique_name_per_owner`)            |
| description | text                |                                             |
| owner_id    | integer             | FOREIGN KEY (`lists_owner_id_fkey`)         |
| created_at  | timestamp           |                                             |
| is_private  | boolean             |                                             |

---

### 6. `places`

| Column Name | Data Type           | Constraints                                  |
|-------------|---------------------|----------------------------------------------|
| id          | integer             | PRIMARY KEY (`places_pkey`)                  |
| list_id     | integer             | FOREIGN KEY (`places_list_id_fkey`)          |
| address     | character varying   |                                              |
| latitude    | double precision    |                                              |
| longitude   | double precision    |                                              |
| rating      | character varying   |                                              |
| notes       | character varying   |                                              |
| created_at  | timestamp           |                                              |
| place_id    | character varying   | UNIQUE (`unique_list_place`)                 |
| name        | character varying   |                                              |
| visit_status| character varying   |                                              |

---

### 7. `tags`

| Column Name | Data Type           | Constraints                                |
|-------------|---------------------|--------------------------------------------|
| id          | integer             | PRIMARY KEY (`tags_pkey`)                  |
| place_id    | integer             | FOREIGN KEY (`tags_place_id_fkey`)         |
| tag         | character varying   |                                            |
| place_id + tag |                  | UNIQUE (`unique_tag_per_place`)            |

---

### 8. `user_follows`

| Column Name | Data Type           | Constraints                                      |
|-------------|---------------------|--------------------------------------------------|
| follower_id | integer             | FOREIGN KEY (`user_follows_follower_id_fkey`)   |
| followed_id | integer             | FOREIGN KEY (`user_follows_followed_id_fkey`)   |
| created_at  | timestamp           |                                                  |
| follower_id + followed_id |       | PRIMARY KEY (`user_follows_pkey`)              |

---

### 9. `users`

| Column Name  | Data Type         | Constraints                             |
|--------------|-------------------|-----------------------------------------|
| id           | integer           | PRIMARY KEY (`users_pkey`)              |
| email        | character varying | UNIQUE (`users_email_key`)              |
| username     | character varying | UNIQUE (`users_username_key`)           |
| firebase_uid | character varying | UNIQUE (`users_firebase_uid_key`)       |
| created_at   | timestamp         |                                         |

