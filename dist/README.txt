MusicMount Tool
---------------

MusicMount.org provides an API and tools to access your music from your mobile devices.
The MusicMount tool is used to build a static MusicMount site, ready to be deployed
to a web server.

Live Site
---------

Usage: Launch in-memory MusicMount server from music in <music_folder>

         <musicFolder>    input folder (containing the music library)

Folders must be local.

Options:
       --retina           double image resolution
       --grouping         use grouping tag to group album tracks
       --unknownGenre     report missing genre as 'Unknown'
       --noTrackIndex     do not generate a track index
       --noVariousArtists exclude 'Various Artists' from album artist index
       --port <port>      launch HTTP server on specified port (default 8080)
       --user <user>      login user id
       --password <pass>  login password
       --full             full parse, don't use asset store
       --verbose          more detailed console output

Build Site
----------

Usage: java -jar lib/musicmount-${project.version}.jar build [options] <musicFolder> <mountFolder>

Generate static MusicMount site from music in <musicFolder> into <mountFolder>

         <musicFolder>    input folder (containing the music library)
         <mountFolder>    output folder (to contain the generated site)

Folders may be local directory paths or smb|http|https URLs, e.g. smb://user:pass@host/path/

Options:
       --music <path>     music path, default is relative path from <mountFolder> to <musicFolder>
       --base <folder>    base folder, <musicFolder> and <mountFolder> are relative to this folder
       --retina           double image resolution
       --grouping         use grouping tag to group album tracks
       --unknownGenre     report missing genre as 'Unknown'
       --noTrackIndex     do not generate a track index
       --noVariousArtists exclude 'Various Artists' from album artist index
       --directoryIndex   use 'path/' instead of 'path/index.json'
       --full             full parse, don't use asset store
       --pretty           pretty-print JSON documents
       --verbose          more detailed console output

Test Site
---------

Usage: java -jar lib/musicmount-${project.version}.jar test [options] [<musicFolder>] <mountFolder>

Launch static MusicMount site in <mountFolder> with music from <musicFolder>

         <musicFolder>    input folder (containing the music library)
         <mountFolder>    output folder (to contain the generated site)

Folders must be local.

Options:
       --music <path>     music path, default is relative path from <mountFolder> to <musicFolder>
       --port <port>      launch HTTP server on specified port (default 8080)
       --user <user>      login user id
       --password <pass>  login password
       --verbose          more detailed console output

 JavaFX UI
 ---------
 
 Usage: java -jar musicmount-ui.jar
 
 The UI does not support remote URLs (CIFS/WebDAV).