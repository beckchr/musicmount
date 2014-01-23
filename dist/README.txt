MusicMount Tool
---------------

MusicMount.org provides an API and tools to access your music from your mobile devices.
The MusicMount tool is used to build a static MusicMount site, ready to be deployed
to a web server.

Build Site
----------

Usage: java -jar musicmount-${project.version}.jar build [options] <musicFolder> <mountFolder>

Generate MusicMount site from music in <musicFolder> into <mountFolder>

         <musicFolder>   input folder (containing the music library)
         <mountFolder>   output folder (to contain the generated site)

Folders may be local directory paths or smb|http|https URLs, e.g. smb://user:pass@host/path/

Options:
       --music <path>     music path, default is relative path from <mountFolder> to <musicFolder>
       --base <folder>    base folder, <musicFolder> and <mountFolder> are relative to this folder
       --retina           double image resolution
       --full             full parse, don't use track store
       --grouping         use grouping tag to group album tracks
       --unknownGenre     report missing genre as 'Unknown'
       --noVariousArtists exclude 'Various Artists' from album artist index
       --directoryIndex   use 'path/' instead of 'path/index.json'
       --normalize <form> normalize asset paths, 'NFC'|'NFD' (experimental)
       --pretty           pretty-print JSON documents
       --verbose          more detailed console output

Test Site
---------

Usage: java -jar musicmount-${project.version}.jar test [options] [<musicFolder>] <mountFolder>

Launch MusicMount site in <mountFolder> with music from <musicFolder>

         <musicFolder>   input folder (containing the music library)
         <mountFolder>   output folder (to contain the generated site)

Folders must be local.

Options:
       --music <path>    music path prefix, default is 'music'
       --port <port>     launch HTTP server on specified port (default 8080)
       --user <user>     login user id
       --password <pass> login password
       --verbose         more detailed console output
