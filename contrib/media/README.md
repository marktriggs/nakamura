Nakamura media bundle
=====================

This module provides facilities for integrating media platforms
(currently Brightcove and Matterhorn) into Sakai OAE.  Through this
module, you can:

  * Intercept media files uploaded to Sakai OAE and send them to a
    remote media platform for transcoding and other processing.

  * Keep the file content of uploaded media files outside of Sakai
    OAE, storing it only on the remote media platform.

  * Replicate the metadata changes to media files in Sakai OAE with
    the remote media platform.  Changes to a media file's title,
    description and tags in Sakai OAE are automatically propagated to
    the remote media platform.

  * Embed the media player of the remote media platform in Sakai OAE.


# A typical use case

Here's an example of how the whole thing fits together:

  * An instructor uploads a lecture video to a group in Sakai OAE
    using the standard 'Upload file' mechanism.

  * Their video file is stored on the Sakai OAE server in a holding
    area.  Behind the scenes, the media bundle picks up the uploaded
    file, recognizes it as a supported media file, and begins
    transferring it to the remote media platform.

  * The instructor views their newly created content profile page for
    the uploaded video.  Since the video is still being uploaded to
    the remote media platform, a progress bar is displayed to indicate
    that the video is still processing.

  * The transfer to the remote media platform completes, and the video
    is converted into a streaming format (such as MP4).

  * The instructor's content profile page automatically refreshes when
    the video is ready to play, and they can now play back the video
    using the embedded player of the remote media platform.

  * The instructor adds some tags to the video and sets a description.
    These properties are picked up by the media bundle and associated
    with the entry for the video file on the remote media platform.

  * When the instructor's course ends, they delete the content profile
    page from Sakai OAE.  This triggers the video to be deleted from
    the remote media platform as well.


# Architectural overview

As much as possible, the media bundle operates asynchronously.  From
the user's point of view, they upload a media file to Sakai OAE as
normal, and the rest happens in the background.  This ensures that the
user interface remains responsive when dealing with large files and
geographically dispersed media platforms.

The core of the media bundle is an asynchronous job scheduler: it
ensures that updates to the remote media platform are never lost once
accepted--even in the presence of local and remote failures--and it
carefully enforces concurrency control, so that concurrent updates
don't overwrite each other.  The components of the media bundle are
summarized in the following subsections.


## The media coordinator

The media coordinator is the heart of the media bundle--all the smarts
are here.  It operates in a continuous loop of:

  * Taking a job from a JMS queue, corresponding to a content object
    that has been created, updated or deleted

  * Inspecting that content object to see if it's a media file of
    interest (and ignoring it if it isn't)

  * Inspecting the state of that media content object to see how it
    changed since the last time it was seen

  * Replicating those updates to the remote media platform

  * Marking the job as completed

Or it would, if life were that simple.  In reality, it has to deal
with a bunch of messy stuff:

  * Anything can fail at any time, so jobs that can't be completed
    successfully are marked for retry after a (configurable) delay.
    Jobs that have failed a certain (configurable) number of times are
    discarded.  On startup, all jobs not marked as completed are
    retried, so no jobs are lost if the JVM crashes.

  * Talking to the remote media platform can be slow, so the media
    coordinator uses a pool of worker threads to do the interaction,
    allowing multiple update requests to be handled simultaneously.
    As each worker completes a job, it signals completion by pushing
    the job back onto a "completed" queue that the media coordinator
    monitors.

  * The media coordinator ensures that the worker threads are always
    working on different content objects--updates to a single content
    object are always serialized.

  * To be able to detect changes and match up content IDs with the IDs
    used by the remote media platform, the media coordinator needs to
    store some information about the last observed state of each
    content object.  It stores this data in a "media node"--a separate
    content node in Sakai OAE that stores the structured replication
    data used by the media coordinator.

    This is stored separately to the content profile object to avoid
    needing to make concurrent writes to the object that other parts
    of Sakai OAE could be updating.  It also means the replication
    state is not lost when the original content object is deleted.

So, that's the messier version: the media coordinator receives jobs
corresponding to updated content objects in Sakai OAE; checks whether
they correspond to media objects and, if so, works out what changed;
portions those jobs out to a worker pool, being very careful not to
give two different workers jobs for the same content object; gathers
up the finished jobs and marks them as completed; detects failed jobs
and retries them every so often; avoids various concurrency pitfalls,
and all while being able to pick up again after some sleep-deprived
sysadmin kill -9's the wrong process ID.

I'm sorry, I thought you said "take your self-pity and run with it".


## The media service

Fortunately, most people don't need to worry about the media
coordinator.  If you want to add support for a new remote media
platform, you just need to implement the `MediaService` interface.
You implement methods for creating/updating/deleting/querying media
content and metadata (interacting with the remote media platform's
API), and your methods will get called by one of the media
coordinator's workers whenever a video file has been changed.


## Media nodes

Media nodes store the information used by the media coordinator to
track changes successive changes to content objects in Sakai OAE and
match them up to the entries in the remote media platform.  If a
content node's path is `mdtmYt5aa`, its media node's path is
`mdtmYt5aa-medianode`.  

Here's an example:

     {
         "_created": 1345010015133, 
         "_createdBy": "admin", 
         "_id": "jTfk0OadEeGxuzDAwKgKmw+", 
         "_lastModified": 1345010015133, 
         "_lastModifiedBy": "admin", 
         "_path": "mdtmYt5aa-medianode", 
         "replicationStatus": {
             "Z51t0OadEeGxuzDAwKgKmw+": {
                 "_created": 1345010015212, 
                 "_createdBy": "admin", 
                 "_id": "jUPywOadEeGxuzDAwKgKmw+", 
                 "_lastModified": 1345045842135, 
                 "_lastModifiedBy": "admin", 
                 "_path": "mdtmYt5aa-medianode/replicationStatus/Z51t0OadEeGxuzDAwKgKmw+", 
                 "bodyMediaId": "{\"workflowId\":\"576\",\"mediaPackageId\":\"7c3394c9-d024-4526-b254-696ade518bdc\",\"trackId\":\"3b8e055c-7b44-48e5-9a42-1bf3b8d86b21\",\"metadataId\":\"209f8d90-9b5b-41a3-8c48-1c2b9174f192\"}", 
                 "bodyUploaded": "Y", 
                 "metadataVersion": -178738461, 
                 "readyToPlay": "Y"
             }, 
             "_created": 1345010015169, 
             "_createdBy": "admin", 
             "_id": "jT1jEOadEeGxuzDAwKgKmw+", 
             "_lastModified": 1345010015169, 
             "_lastModifiedBy": "admin", 
             "_path": "mdtmYt5aa-medianode/replicationStatus"
         }
     }

Under the "replicationStatus" property is an object for each *version*
of the content object in Sakai OAE--each version might have different
metadata and a different video file associated with it, so a single
content object in Sakai OAE might correspond to many entries in the
remote media platform.

The replication status entry for each version stores flags to indicate
whether the video has been uploaded yet and, if so, whether it's ready
to play.  It also stores a hash code of the content object's metadata
from the last time it was replicated, in order to be able to detect
when the metadata needs to be resupplied.  Finally, it stores the ID
that the remote media platform assigned to each version (the
`bodyMediaId`).

An important property of the system's architecture is that there's
enough information here to interrogate a content object and work out
what changed since it was last seen.  This frees the media
coordinator from attempting to infer meaning from OSGi events, and
avoids potential reliability problems from OSGi events not being
delivered.  From a simple message like "mdtmYt5aa was changed", the
media coordinator has enough information to do whatever's needed to
bring the remote media platform up to date.


## The media listener

That's almost the whole story: we have the media coordinator receiving
notifications via JMS, figuring out what changed based on data stored
in media nodes, and replicating those changes via a `MediaService`
implementation.  All that's left is to look at where those
notifications come from in the first place, and where the uploaded
files are stored.

The media listener responds to three types of events that might
involved media content objects: 

  * file uploads, which could be uploaded media files

  * OSGi content update events, which might mean metadata changes

  * OSGi content deleted events, which might mean a media content
    object was removed from Sakai OAE

It signals all of these events to the media coordinator in exactly the
same way: by pushing the path of the added/updated/deleted content
object onto the JMS queue.  The media coordinator does the rest:
figures out what happened, and ignores multiple messages for the same
content object.


### The FileUploadFilter interface

The media bundle led to the development of a new `FileUploadFilter`
interface, which gives modules the option of intercepting (and
potentially replacing) the data stream of an uploaded file.

The media listener implements this new interface to avoid storing
(potentially large) media files in the Sakai OAE repository.  Instead,
it writes the file to a temporary location on the Sakai OAE server,
and deletes it once the media coordinator has successfully uploaded it
to the remote media platform.
