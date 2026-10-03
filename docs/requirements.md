# Requirements

## 1. Project Overview

Private web application to manage my self-hosted Docker instance. Inspired by Portainer and Pterodactyl.

The application should provide a central web interface for managing Docker containers and especially game servers running as Docker containers.

## 2. Goals

* Provide a central web interface for managing the self-hosted Docker instance.
* Provide an overview of all Docker containers and their current state.
* Allow Docker containers to be created, managed, and deleted.
* Make managing game servers easier than managing generic Docker containers.
* Automatically detect containers that represent game servers.
* Allow administrators to manually mark containers as game servers when necessary.
* Provide game-server-specific management and configuration options.
* Allow supported game-server configuration files to be viewed and edited directly from the web application.
* Provide an integrated editor for configuration files.
* Provide role-based access control with configurable permissions.
* Provide administration functionality for users, roles, permissions, announcements, and application maintenance mode.

## 3. Core Features

* Dashboard Page
* Container Details Page
* Create and Delete Containers
* Start / Stop / Force Stop / Restart Containers
* View Container Logs
* Live Container Logs
* View Container Information
* Game Server Detection
* Manual Game Server Classification
* Game Server Profiles
* Game Server Templates
* View and Edit Game Server Configurations
* File Browser for Game Server Configuration Files
* Built-in Editor for Configuration Files
* User Authentication
* User Management
* Role and Permission Management
* Dashboard Announcements
* Application Maintenance Mode
* System Information in the Admin Area

## 4. Dashboard

The dashboard should provide an overview of all Docker containers.

Each container should display at least:

* Container Name
* Container Image
* Container State
* Visual indication of the current state
* Label indicating whether the container is a game server
* Available actions based on the user's permissions

The dashboard should also provide basic container statistics, such as:

* Total number of containers
* Number of running containers
* Number of stopped containers
* Number of detected game servers

The dashboard should allow users to:

* Open the container details page
* Start containers
* Stop containers
* Restart containers

Only actions allowed by the user's permissions should be available.

System information should not be part of the dashboard. It should be available in the Admin Area.

## 5. Container Details

The container details page should provide more detailed information about a Docker container.

The page should contain the following areas:

### General Information

* Container Name
* Container ID
* Container Image
* Container State
* Creation date
* Uptime where applicable

### Storage

* Docker Volumes
* Bind Mounts
* Relevant storage information

### Configuration

* Environment Variables
* Restart Policy
* Relevant Docker configuration

### Actions

Available actions should depend on the user's permissions.

At minimum, supported container actions should include:

* Start
* Stop
* Force Stop
* Restart
* Delete

The container details page should also provide access to container logs.

Container logs are read-only.

The application should support live container logs.

## 6. Container Creation and Management

The application should allow administrators to create Docker containers.

The initial container creation functionality should provide an important and practical subset of Docker configuration options rather than attempting to expose every possible Docker option immediately.

The architecture should allow additional Docker configuration options to be added later without requiring a complete redesign.

Relevant configuration options may include:

* Container Name
* Docker Image
* Ports
* Volumes
* Environment Variables
* Restart Policy
* Networks
* Other relevant Docker configuration

Game server templates should be supported in addition to generic container creation.

Game server templates should provide predefined configurations for specific game servers while still allowing the resulting configuration to be customized.

Templates may initially provide predefined Docker configuration and should be extensible to support more specialized setup flows in the future.

Deleting a container must not automatically delete its associated volumes.

When deleting a container, the application should clearly indicate whether associated volumes will remain and require explicit confirmation for any operation that would remove persistent data.

Only administrators may delete containers by default.

## 7. Game Server Management

The application should provide additional functionality for Docker containers identified as game servers.

### Game Server Detection

The application should automatically detect whether a Docker container represents a game server.

Detection may use multiple criteria, such as:

* Docker image
* Docker labels
* Exposed ports
* Other relevant container information

The exact detection mechanism should remain extensible.

Administrators should also be able to manually classify a container as a game server or change its classification when automatic detection is incorrect.

### Game Server Profiles

The application should support game server-specific profiles.

A profile represents a known game server type and can provide game-specific functionality, configuration files, templates, and management options.

The application should also support generic game server classification for game servers that do not yet have a dedicated profile.

### Game Server Templates

Game server templates should provide predefined configurations for supported game servers.

Templates should be extensible and may initially provide predefined Docker container configurations.

In the future, templates may provide more specialized configuration flows for individual games.

### Game Server Configuration

Depending on the detected or selected game server profile, the application should provide access to relevant configuration files.

Examples include:

* Minecraft `server.properties`
* Game-specific configuration files
* Relevant configuration directories

The application should provide both:

* Direct access to known/relevant configuration files
* A file browser for supported game-server configuration files

Users with the required permission should be able to view and edit supported configuration files.

Configuration files should be edited using an integrated editor such as Monaco Editor.

## 8. Authentication

Users authenticate using a username and password.

Users should be stored in MongoDB.

Users must be manually created by an administrator.

When creating a user, an administrator should have an option to generate a randomized password.

The generated password should:

* Be displayed to the administrator after generation
* Be hidden by default
* Have an option to reveal it
* Allow the administrator to copy the user's credentials to the clipboard

The existing password should not be retrievable in plaintext after it has been stored.

Administrators should be able to generate a new password when a password reset is required.

Users should be encouraged to change their password after their first login, but changing the password on first login is not mandatory.

Users who forget their password should be able to:

* Have an administrator reset their password
* Use a password reset process via email

## 9. Roles and Permissions

The application should use role-based access control.

The permission system should be independent from the predefined roles so that permissions can be assigned flexibly.

The application should provide the following default roles:

* Admin
* Moderator
* User

Administrators should be able to:

* Create roles
* Edit roles
* Delete roles where appropriate
* Assign permissions to roles
* Change the role assigned to a user

Administrators have unrestricted access to the application.

The default permissions of the predefined roles should provide the following general behavior:

### Admin

* Full access to the application
* Manage users
* Manage roles
* Manage permissions
* Manage containers
* Manage game servers
* Manage announcements
* Manage maintenance mode
* View system information

### Moderator

* Access dashboard
* Access container details
* View container logs
* Start containers
* Stop containers
* Restart containers

### User

* Access dashboard
* Access container details
* View container logs
* Read-only access to containers

Permissions should be configurable so that these default role capabilities can be changed by an administrator.

For example, a permission to edit game-server configuration files may initially be assigned only to administrators but should be assignable to other roles later.

## 10. User Management

Administrators should be able to manage application users.

At minimum, user management should support:

* Create users
* View users
* Edit users
* Change user roles
* Reset user passwords
* Deactivate users
* Delete users

Deactivated users should remain stored in the application but must not be able to authenticate.

User deletion should require explicit confirmation.

## 11. Admin Page / Area

The application should provide a dedicated Admin Area.

The Admin Area should provide access to administrative functionality, including:

* User management
* Role management
* Permission management
* Announcement management
* Maintenance mode management
* System information

System information should include relevant information about the application and the managed infrastructure.

The exact system information displayed should be determined during implementation based on the available Docker host and application information.

## 12. Announcements

Administrators should be able to create and manage announcements displayed on the dashboard.

Multiple announcements may be active at the same time.

An announcement should support:

* Title
* Message
* Active / Inactive state
* Optional start time
* Optional end time

An announcement may have no end time.

If an end time is configured, the announcement must automatically stop being displayed once the end time has been reached.

Administrators should be able to:

* Create announcements
* Edit announcements
* Activate/deactivate announcements
* Delete announcements

Deleting an announcement should require explicit confirmation.

## 13. Maintenance Mode

Administrators should be able to enable and disable maintenance mode for the entire application.

When maintenance mode is active:

* Non-authenticated users should see a dedicated maintenance page.
* Non-admin users should not be able to access the application.
* Administrators should retain access to the application.
* An administrator-configurable information text should be displayed.

Maintenance mode does not require an automatic end time.

Enabling and disabling maintenance mode should be restricted to administrators.

## 14. Error Handling

The application should provide clear feedback when an operation fails.

Possible failures include:

* Docker host unavailable
* SSH connection failure
* Container no longer exists
* Docker operation failure
* Configuration file unavailable
* Permission denied
* Other infrastructure-related errors

Normal users should receive a clear but non-technical error message.

Administrators may be shown additional technical information and the full error details to assist with troubleshooting.

Technical error details should not be unnecessarily exposed to normal users.

## 15. Destructive Actions

Destructive or potentially disruptive actions should require explicit confirmation.

Confirmation should be presented using a modal.

Examples include:

* Delete container
* Delete user
* Delete announcement
* Force stop container
* Remove persistent data or volumes

The confirmation should clearly describe the action being performed and, where applicable, its consequences.

The application should avoid destructive operations unless explicitly confirmed by the user.

## 16. Non-Goals

The initial version of the application is not intended to provide functionality for:

* Kubernetes management
* Docker Swarm management
* Multi-server Docker management
* Complex infrastructure orchestration
* Billing or payment systems
* User self-service infrastructure provisioning
* A general-purpose plugin marketplace
* Full-scale monitoring and observability
* Automatic backup management

The application should focus on Docker container management, game server management, configuration management, authentication, authorization, and administration.

Features outside of this scope should not be implemented unless explicitly requested.

## 17. Future Ideas

Potential future functionality may include:

* Additional game server profiles
* Additional game server templates
* More Docker configuration options during container creation
* More specialized game server setup flows
* Additional permissions
* Additional infrastructure information
* Additional management functionality

Future ideas should not be implemented unless explicitly requested.
