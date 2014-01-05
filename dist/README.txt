MusicMount Tool
---------------

MusicMount.org provides an API and tools to access your music from your mobile devices.
The MusicMount tool is used to build a static MusicMount site, ready to be deployed
to a web server.

Build Site
----------

Usage: java -jar musicmount-${project.version}.jar build [options] <music_folder> <mount_folder>

Generate MusicMount site from music in [<music_folder>] into <mount_folder>

         <music_folder>   input folder, default is <mount_folder>/<value of --music option>
         <mount_folder>   output folder to contain the generated site

Options:
       --music <path>     music path prefix, default is 'music'
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

Usage: java -jar musicmount-${project.version}.jar test [options] [<music_folder>] <mount_folder>

Launch MusicMount site in <mount_folder> with music from <music_folder>

         <music_folder>   input folder, default is <mount_folder>/<value of --music option>
         <mount_folder>   output folder to contain the generated site

Options:
       --music <path>     music path prefix, default is 'music'
       --port <port>      launch HTTP server on specified port (default 8080)
       --user <user>      login user id
       --password <pass>  login password
       --verbose          more detailed console output
