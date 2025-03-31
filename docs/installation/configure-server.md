# Configure Server

_Is your server running? See [Installation > Getting Started](getting-started.md) first._

When the server has started without errors, the web client will be available
at [localhost:8888](https://localhost:8888) or whatever port you've specified.

## Create an Admin User

After starting a new AnyStream instance, the first user signup does not require an invitation code and will be granted
global permissions.

!!! danger

    It is important to register an admin user immediately after starting AnyStream for the first time.
    If left incomplete, unauthorized users my be able to modify system files via AnyStream.

## Importing Media

Importing your media files starts by adding a directory to a Library.

Before adding a library directory, ensure your media files follow the guidelines in
[File and Directory Naming](../2-library-management.md#file-and-directory-naming).

1. Navigate to Settings > Libraries
2. Click "Edit" on the desired Library
3. Click "Add Folder"
4. Select or paste the folder path and click "Add"

That's it!  All the media files will be scanned and metadata will be downloaded.
You can navigate to the home page to see your library populate in real time.


## Inviting Users

You can add new users manually or create unique URLs to share which allow a single person to create an account with the
desired permissions.

To create an invitation URL:

- Navigate to Settings > Users, then click "Manage Invites".
- Select the permissions required by the new User, then click "Create Invite".
- The new invite will be added to the list and a sharable signup link will be copied to your clipboard.

