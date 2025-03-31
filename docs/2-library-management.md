# Library Management

AnyStream libraries represents a single kind of media (Movies, TV, etc.) and contain a list of content directories
with are regularly scanned to downloading metadata (title, posters, ratings, etc.) and analyze media file contents
(audio/video codecs, bitrate, etc.).

## File and Directory Naming

To understand the file and directory structure of your media files, they must follow a specific naming conventions
and folder structures.

Without following these requirements, your library may still be understood by AnyStream but alternative
naming/structures are not officially supported yet and may result in subtle issues viewing your library.

### Basic Requirements

There are two common requirements for proper scanning of your media library that apply to every kind of content:

1. Each type of content (TV Shows, Movies, Music, etc.) are in their own directory.

        /../media/TV/
        /../media/Movies/
        /../media/Music/

2. Media files are not located directly within the content directory, i.e. `../videos/TV` and `../videos/Movies` only
   contain other directories for specific shows and movies.

         /../media/TV/Noir (2001)/
         /../media/Movies/Primer (2004)/
         /../media/Music/Chairlift/

3. Content folders and files should include the original release year whenever possible to improve accuracy when
   automatically matching metadata.

### Movies

Movie files must be contained within a folder that shares the file's name.

For example, AnyStream expects to find movies structure like:

      /../Movies/The Cabinet of Dr. Caligari (1920)
      /../Movies/The Cabinet of Dr. Caligari (1920)/The Cabinet of Dr. Caligari (1920).mp4

Note the first line is the folder with the `<movie title> (<year>)` format, and the second line is the movie file with
a matching name and the video file extension.

### TV Shows

TV Shows must have a directory with the show's title containing season folders which in turn contain individual episode
video files.

For example, AnyStream expects to find tv shows structured like:

      /../TV/Mister Rogers Neighborhood (1968)
      /../TV/Mister Rogers Neighborhood (1968)/Season 01/S01E01 - Change The First Program.mp4
      /../TV/Mister Rogers Neighborhood (1968)/Season 01/S01E02 - King Friday Challenges Change.mp4

Note each season has a folder with the `Season <number>` format where the number optionally contains a leading zero.

Each episode is contained within it's respective season folder using the
`S<season number>E<episode number> - <episode name>` format where the season and episode numbers contains optional
leading zeros.

Episode files may optionally use the `<show name> - S00E00 - <episode title>` format but the directory structure must
still contain a parent show and season folder.

### Subtitle Files


