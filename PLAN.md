PROJECT-GOAL:

Multi-client chat server with message history.
Networking : TCP sockets, client-server model, broadcast and private messages.
OS/Threads: a thread per client (or a thread pool), a synchronized shared client list.
JDBC : stores users, chat rooms, and message logs.

I want to create a chatting website, implementing all the above mentioned features with the given tech stack:
Frontend: HTML, CSS, JavaScript 
Backend : SpringBoot
No deviation from techstack.

MVP (must have)
•	Register with username and password
•	Login and logout
•	Passwords hashed with BCrypt
•	Session-based auth, reused for the WebSocket handshake
•	Broadcast to everyone (default “General” room)
•	Private one-to-one chat
•	User search to start a private chat
•	Create, join, leave, and list chat rooms
•	Live delivery to all connected clients
•	Every message saved to MySQL via JDBC
•	Last N messages load when you open a chat
•	Scroll up to load older ones
•	TCP server on port 9000
•	Thread pool, one handler per client
•	Synchronized shared client list
•	Clean cleanup when a client disconnects
•	Online/offline green dot
•	Last seen time
•	Online users list
•	Unread count badge per chat
•	Message timestamps
•	Auto-reconnect if the WebSocket drops
•	Typing indicator
•	Sent / delivered / read ticks
•	Edit and delete your own messages
•	Room member list and room owner
•	Room admin: kick a member

TECHSTACK

Browser (HTML/CSS/JS)
│ WebSocket
▼
Spring WebSocket handler ← one bridge per browser tab
│ raw TCP (java.net.Socket)
▼
ChatServer (ServerSocket :9000)
├─ thread pool, one ClientHandler per connection
├─ synchronized shared client list
└─ JDBC → MySQL













PROJECT-PLAN: Multi-client Chat Server with Message History

Approach: Backend-first development. Build and test every backend component independently before connecting the AI-generated frontend.

Your specified stack remains unchanged: Java, Spring Boot, JDBC, MySQL, Java Networking (ServerSocket, Socket), WebSocket, HTML, CSS and JavaScript.
Phase 1 — Project setup
Foundation
1. Create a Spring Boot project.
2. Configure Maven dependencies:
   - Spring Web
   - Spring WebSocket
   - Spring JDBC
   - MySQL Driver
   - Spring Security (for BCrypt password hashing)
3. Set up MySQL database.
4. Configure application.properties.
5. Establish the project package structure:
   - config
   - controller
   - service
   - repository
   - model
   - network
   - websocket
   - dto
Deliverable: Spring Boot application connects successfully to MySQL.
Phase 2 — Database design
Backend
Design and create these tables:
Table	Purpose
users	User credentials and profile
chat_rooms	Room information and owner
room_members	Users belonging to rooms
messages	All chat messages
private_chats	One-to-one chat identification

Implement JDBC repository classes for CRUD operations.

Deliverable: You can insert, retrieve, update and delete records using JDBC.
Phase 3 — Authentication
1. Implement user registration.
2. Hash passwords using BCrypt.
3. Implement login validation.
4. Implement session-based authentication.
5. Implement logout and session invalidation.
6. Implement authentication checks for protected endpoints.
Deliverable: Complete registration/login/logout functionality, tested using Postman.
Phase 4 — Core Java Networking
Important: Java Networking
Build the TCP server independently before integrating Spring Boot.
1. Create a ChatServer using ServerSocket on port 9000.
2. Implement ClientHandler using Socket.
3. Create a thread pool using ExecutorService.
4. Maintain a shared collection of connected clients.
5. Implement thread-safe client registration and removal.
6. Implement message broadcasting.
7. Implement private message routing.
8. Handle client disconnections and resource cleanup.
Deliverable: Multiple terminal clients can connect, broadcast and privately exchange messages.

This phase demonstrates your actual Java Networking and multithreading implementation.
Phase 5 — Integrate JDBC with TCP Server
1. Connect the TCP server to your JDBC repositories.
2. Save every incoming message to MySQL.
3. Retrieve recent messages when a client joins a chat.
4. Implement pagination for older messages.
5. Associate messages with users and rooms/private conversations.
6. Implement message timestamps.
Deliverable: Messages are delivered live and persist even after clients disconnect.
Phase 6 — Spring Boot + WebSocket integration
Integration
1. Implement Spring WebSocket configuration.
2. Create a WebSocket handler.
3. Authenticate WebSocket connections using the existing HTTP session.
4. Create a bridge between WebSocket clients and your TCP server.
5. Each browser connection gets its own TCP connection.
6. Forward browser messages to TCP.
7. Forward TCP responses to the appropriate WebSocket clients.
8. Handle connection closure and cleanup.
Deliverable: Browser clients can communicate through your Java TCP server.
Phase 7 — Chat features
Implement the remaining features in this order:
Order	Feature
1	General room broadcasting
2	Create, join and leave rooms
3	Private one-to-one chat
4	User search
5	Online/offline status
6	Last seen
7	Online users list
8	Room member list and ownership
9	Room admin and kick member
10	Typing indicator
11	Unread message count
12	Sent, delivered and read receipts
13	Edit and delete messages
14	WebSocket auto-reconnection

Deliverable: Complete backend functionality.
Phase 8 — API and backend testing
Test the following independently:
- Registration and authentication.
- Session management.
- JDBC CRUD operations.
- Multiple simultaneous TCP connections.
- Broadcasting to multiple clients.
- Private message routing.
- Room membership and permissions.
- Message persistence.
- Message history and pagination.
- WebSocket authentication.
- Disconnection and reconnection.
- Concurrent message delivery.
Use Postman for HTTP APIs and multiple terminal clients for TCP testing.
Phase 9 — Frontend integration
AI-generated frontend
Only after the backend is stable:
1. Generate the HTML/CSS/JavaScript chat interface using AI.
2. Connect login and registration pages to REST APIs.
3. Connect the chat interface using WebSocket.
4. Integrate room switching and private conversations.
5. Connect message history and pagination.
6. Integrate online status, typing indicators and receipts.
7. Test the complete application.

Recommended development order
This is the order I recommend you actually follow. Don't jump between phases.
1. Spring Boot + MySQL setup
   
   Database connectivity and project structure
2. Database schema + JDBC
   
   All database operations
3. Authentication + sessions
   
   Registration, login, BCrypt
4. Standalone TCP chat server
   
   Sockets, threads, broadcasting, private messages
5. TCP + JDBC integration
   
   Persistent messaging and history
6. WebSocket + TCP bridge
   
   Browser-to-server communication
7. Advanced chat features
   
   Rooms, presence, receipts, permissions
8. Testing and frontend integration
   
   Complete application

Important architectural decision
Your architecture has two communication layers:
Browser frontend

HTML / CSS / JavaScript

Spring Boot WebSocket Handler

One TCP bridge per browser connection

Java TCP Chat Server

ServerSocket :9000 · ClientHandler · Thread pool

JDBC Repository

MySQL database


One important rule: Don't implement two separate messaging systems. The TCP server should own the core chat routing and broadcasting logic. Spring WebSocket should act as the browser communication bridge.

Also, keep authentication and database operations in Spring Boot services/repositories rather than duplicating them inside the TCP server.