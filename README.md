# MusicMount Tool

[_MusicMount.org_](http://musicmount.org) provides an API and tools to access
your music from your mobile devices. The _MusicMount_ tool is used to build a
static _MusicMount_ site, ready to be deployed to a web server.

The _MusicMount_ tool scans your music folder for MP3 and M4A (Apple) audio files,
extracts metadata like track names, album titles, artists, genres, cover art, etc.
It then computes an album-centric model and generates a static _MusicMount_ site
(also known as _mount_) consisting of JSON and image files.

A _MusicMount_ site can be hosted on a NAS, Raspberry Pi or even on a wireless drive.
Once your site is built and deployed, you can access your site from your mobile device.

![_MusicMount_ for iOS](http://musicmount.org/images/musicmount-ios.jpg)

## Features

- Easy to use graphical user interface
- High performance ID3/MP4 tag parsers
- Embedded test server to get you started quickly
- Remote file system support via CIFS (SMB) or WebDAV
- Incremental builds -- only parses newly added and modified tracks

## Documentation

Documentation is available [here](http://musicmount.org/tool/).

## Change History

#### Version 1.5.1 (2014/06/04)

- fixed m4a issue with parsing year with length != 4
- generate scripts via "Application Assembler" (maven plugin)
- make Jetty the default server (was: tomcat)
- [UI] update loggers with ConsoleHandler after redirecting stdout/stderr
- [UI] cancel running service before closing stage
- [UI] don't make "Full Parse/Build" option a user preference
- minor improvements/fixes

#### Version 1.5 (2014/05/02)

- generate track index
- added JavaFX user interface
- minor improvements/fixes

#### Version 1.4.1 (2014/04/04)

- upgrade dependencies to StAXON 1.3, Sardine 5.1
- increased ID3 text buffer size for comments/lyrics
- improved progress monitoring

#### Version 1.4 (2014/01/24)

- test server: root context is now `/musicmount`, support `--music ../music` (one level up only)
- improved image task building
- expect <mountFolder> to exist (do not create)
- ask for password for URLs like `http://user@host/path`
- `--music <path>` defaults to relative path from `<mountFolder>` to `<musicFolder>`
- remote file systems support (smb + webdav)
- do not use directory index by default; `--directoryIndex` replaces `--noDirectoryIndex`
- support absolute `--music <path>`, e.g. `/music`
- refactored audio/tag parsing stuff into "audio" package
- added "link" command
- use lexicographically increasing `yyyy-MM-dd'T'HH:mm:ss` date pattern for updateToken
- implemented new ID3/MP3 tag parser, dropped _mp3agic_
- redesigned asset store (update & sync)

#### Version 1.3 (2013/12/12)

- fixed merging of album from VA into album artist
- replaced _imgscalr_ with _thumbnailator_ library
- simplified image scaling
- log exception if asset store loading failed
- use maximum track year as album year
- fixed: unknown artist not shown in artist index
- asset store format change: `apiVersion` --> `version`

#### Version 1.2 (2013/12/08)

- mark modified tracks as "changed"
- support album track grouping
- support `updateToken` API
- added `--verbose`option
- changed title of album tracks without disc to "Tracks"

#### Version 1.1.6 (2013/11/26)

- preserve album id for modified tracks
- delete image if write failed

#### Version 1.1.5 (2013/11/25)

- do not mark VA album collection as "Compilations"

#### Version 1.1.4 (2013/11/22)

- trim track/album/artist title etc
- update to _mp3agic_ 0.8.2
- sort compilations with album artist by year (and title)

#### Version 1.1.3 (2013/09/07)

- if (track) artist is missing, use album artist as default
- consult ID3v1 tag if ID3v2 info is incomplete
- use oldest available album cover as artist image

#### Version 1.1.2 (2013/06/11)

- fixed: `AssetStore` not updating timestamp

#### Version 1.1.1 (2013/06/06)

- generate images using multiple threads

#### Version 1.1 (2013/05/24)

- implemented new mp4 tag parser, dropped _jaudiotagger_
- use _mp3agic_ to parse MP3 tags

#### Version 1.0 (2013/04/26)

- Initial release

## License

Available under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).


_(c) 2013, 2014 Odysseus Software_