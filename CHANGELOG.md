Changelog
=========

Version 0.3.7 November 7, 2017
-----------------------------
- Support HTTPS when upload or download files.
- Support uploading to root container.
- Fix content length property when upload or download files.
- Fix a null pointer exception when upgrading from last version.

Version 0.3.6 June 7, 2017
-----------------------------
- Support blob properties when uploading files.
- Support blob metadata when upload files.
- Added an option to delete original files after download.
- Added an option to auto detect content type when upload files.
- Support file storage.
- Added pipeline support.

Version 0.3.5 April 12, 2017
-----------------------------
- Fixed an issue on Windows masters that caused uploaded files to get locked on the local machine and couldn't be deleted.

Version 0.3.4 March 14, 2017
-----------------------------
- The copyartifact dependency is mandatory. [JENKINS-41713](https://issues.jenkins-ci.org/browse/JENKINS-41713)
- Fixed the artifact download links. [JENKINS-42726](https://issues.jenkins-ci.org/browse/JENKINS-42726)
- The "Microsoft Azure Storage" credentials can now be updated. [#43](https://github.com/jenkinsci/windows-azure-storage-plugin/issues/43)

Version 0.3.3 February 16, 2017
-----------------------------
- Storage account credentials are more secure now. They have moved to Jenkins credential store.
- Once you upgrade to 0.3.3 from older Version, no need to manually configure existing jobs.

Version 0.3.2 January 26, 2017
-----------------------------
- Storage account key has become a hidden field.
- Added the capability to use managed artifacts, a use case for artifacts can be downloading a known good build or an artifact from an upstream build.
- Artifacts can be downloaded from previous builds.
- Links on the project page (to download) has been fixed. This now allows easier access to download artifacts from Jenkins.
- Downloads are now faster, plugin doesn't need to search the entire container for the correct blobs when using managed artifacts.
- Changes are made inline with Jenkins API, updated Azure Java SDK to provide better output to Jenkins REST API.

Version 0.3.0 September 09, 2014
-----------------------------
- Added easily accessible links for azure artifacts uploaded to blob storage.

Version 0.2.0 April 23, 2014
----------------------------
- Changed the logic of the "make container public" checkbox to apply to newly created containers only
- Added a "download from blob" build action
- Added a "clean container" option to the artifact uploader post-build action
- Some renamings due to branding changes in Azure (Windows Azure changing to Microsoft Azure)

Version 0.1.0 February 12, 2013)
--------------------------------
 - Initial release
