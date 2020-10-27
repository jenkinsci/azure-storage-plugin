Changelog
=========

Version 1.1.6 October 27, 2020
---------------------------
- Bump junit from 4.12 to 4.13.1
- Update maintainer 

Version 1.1.5 January 14, 2020
---------------------------
- Upgrade jcasc test harness 

Version 1.1.4 January 2, 2020
---------------------------
- Fix wrong MD5 value

Version 1.1.3 December 30, 2019
---------------------------
- Fix MD5 missing for large file

Version 1.1.2 November 1, 2019
---------------------------
- Remove check for uploaded count when only uploading archived file

Version 1.1.1 September 30, 2019
---------------------------
- Rename AzureCredentials to AzureStorageAccount (JENKINS-58710)

Version 1.1.0 July 25, 2019
---------------------------
- Add option to clean up virtual path
- Fix potential cleanup container twice
- Fix large file missing metadata

Version 1.0.4 July 17, 2019
---------------------------
- Fix NoClassDefFoundError on agent

Version 1.0.3 July 11, 2019
---------------------------
- Change temporary file name ruling

Version 1.0.2 July 8, 2019
---------------------------
- Fix using Jenkins proxy issue
- Delete temporary directory after uploading
- Fix possible ConcurrentModificationException

Version 1.0.1 June 18, 2019
---------------------------
- Fix missing small files bug

Version 1.0.0 June 11, 2019
---------------------------
- Bump Jenkins baseline to 2.89.4
- Add support for configuration as code
- Fix failing to download when there is a slash in blob name
- Fix download share file ant syntax issue
- Change default blob URL to https
- Fix failing to upload large files to blob

Version 0.3.13 January 31, 2019
---------------------------
- Fix serialize issue from master to agent

Version 0.3.12 January 28, 2019
---------------------------
- Upload files to Blob service on agents using SAS instead of sdk

Version 0.3.11 November 9, 2018
---------------------------
- Upgrade Azure Storage SDK to version 6.1.0 (#132)
- Make compatibility with versions before 0.3.6 (#135)
- Fix naming convention rules not consist issue (#126)
- Fix pipeline with multi azureUpload commands cannot list artifacts action properly (#129)
- Fix cannot upload empty files to share file (#111)


Version 0.3.10 August 16, 2018
---------------------------
- Add support for static website hosting (#119)
- Fix storage account not masked in Jenkins pipeline (#118)
- Fix URL broken for Blue Ocean artifacts (#115)

Version 0.3.9 April 4, 2018
---------------------------
- Do not mark build as unstable if no files are downloaded (JENKINS-42341)
- Support credentials binding for storage account (#99)
- Support for parallel files upload and download (#86)
- Fix broken image in job action (#104)
- Support for Blue Ocean artifacts listing (#101)
- Support for proxy set in Jenkins (#31)
- Option to upload modified artifacts only (#52)
- Support for credentials lookup in [Folders](https://plugins.jenkins.io/cloudbees-folder)

Version 0.3.8 January 12, 2018
------------------------------
- Make container/share name optional in pipeline `azureUpload` (#82)
- Fix download of Azure file artifacts (#84)
- Fix bland window issue while configuring storage credentials globally (#85)
- Explicitly specify MIME type for js files (#89)

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
