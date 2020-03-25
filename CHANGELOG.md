## CVS Plugin Change Log

### Version 2.14 (Feb 28, 2018)

-   [JENKINS-26345](https://issues.jenkins-ci.org/browse/JENKINS-26345)
-   [JENKINS-49574](https://issues.jenkins-ci.org/browse/JENKINS-49574)
-   Use standard JSch library ([PR 47](https://github.com/jenkinsci/cvs-plugin/pull/47))

### Version 2.13 (Jan 18, 2017)

-   Select the checkout timestamp wisely to honor the spirit of quiet
    period ([pull request](https://github.com/jenkinsci/cvs-plugin/pull/32))
-   CVS plugin now works with Jenkins Pipeline
    ([JENKINS-27717](https://issues.jenkins-ci.org/browse/JENKINS-27717))
-   Ignore recusive symlinks during cvs update
    ([JENKINS-23234](https://issues.jenkins-ci.org/browse/JENKINS-23234))

### Version 2.12 (June 10, 2014)

-   Reduce memory usage during changelog handling
    ([JENKINS-19458](https://issues.jenkins-ci.org/browse/JENKINS-19458))
-   Don't prune non directory modules
    ([JENKINS-20317](https://issues.jenkins-ci.org/browse/JENKINS-20317))
-   Don't queue new builds for changes in the current build
    ([JENKINS-19314](https://issues.jenkins-ci.org/browse/JENKINS-19314))
-   German localisation improvements

### Version 2.11 (October 24, 2013)

-   Fix change log retrieval on slaves
    ([JENKINS-20192](https://issues.jenkins-ci.org/browse/JENKINS-20192))
-   Fix issues with files not being updated, being removed, or
    incorrectly backed-up when 'Force Clean Copy' is enabled
    ([JENKINS-17383](https://issues.jenkins-ci.org/browse/JENKINS-17383),
    [JENKINS-20188](https://issues.jenkins-ci.org/browse/JENKINS-20188))

### Version 2.10 (October 21, 2013)

-   Match global authentication rules in a case-insensitive manner on
    hostname
-   Fix empty directory prune when running in quiet mode
    ([JENKINS-18390](https://issues.jenkins-ci.org/browse/JENKINS-18390))
-   Fix ConcurrentModificationException on polling
    ([JENKINS-18329](https://issues.jenkins-ci.org/browse/JENKINS-18329))
-   Fix sporadic ssh connection failures
    ([JENKINS-18591](https://issues.jenkins-ci.org/browse/JENKINS-18591))
-   Fix post checkout change log generation always running on master
    ([JENKINS-13764](https://issues.jenkins-ci.org/browse/JENKINS-13764))
-   Expand tag/branch name during polling
    ([JENKINS-19653](https://issues.jenkins-ci.org/browse/JENKINS-19653))
-   Properly match exclude regions during polling
    ([JENKINS-15826](https://issues.jenkins-ci.org/browse/JENKINS-15826))
-   Don't skip subsequent modules after first successful tag in
    multi-module setup
    ([JENKINS-15735](https://issues.jenkins-ci.org/browse/JENKINS-15735))
-   Don't poll if last checkout hasn't completed to prevent multiple
    builds of same change
    ([JENKINS-19314](https://issues.jenkins-ci.org/browse/JENKINS-19314))
-   Fix polling and workspace updates when CVS server is in a different
    timezone from Jenkins
    ([JENKINS-17383](https://issues.jenkins-ci.org/browse/JENKINS-17383),
    [JENKINS-17965](https://issues.jenkins-ci.org/browse/JENKINS-17965))

### Version 2.9 (June 03, 2013)

-   Prevent removal of tag information during checkout
    ([JENKINS-16314](https://issues.jenkins-ci.org/browse/JENKINS-16314))
-   Improve efficiency of file tagging
    ([JENKINS-15735](https://issues.jenkins-ci.org/browse/JENKINS-15735))
-   Match global authentication entries even when no CVS port is defined
    ([JENKINS-16432](https://issues.jenkins-ci.org/browse/JENKINS-16432))
-   Prevent potential ConcurrentModificationException during polling
-   Allow repository browsers to be defined at the repository level
    rather then the job level
-   Fix an issue with identifying branches/tags in for CVS parameters
    ([JENKINS-17656](https://issues.jenkins-ci.org/browse/JENKINS-17656))
-   Allow CVS to be very quiet in the logs
    ([JENKINS-17470](https://issues.jenkins-ci.org/browse/JENKINS-17470))
-   Fix OpenGrok browser support

### Version 2.8 (February 19, 2013)

-   Add option to force clean update (cvs up -C)
    ([JENKINS-15848](https://issues.jenkins-ci.org/browse/JENKINS-15848))
-   Allow dashes in projestset module names
    ([JENKINS-15525](https://issues.jenkins-ci.org/browse/JENKINS-15525))
-   Resolve issue with sticky date cleanup if workspace is flattened
    ([JENKINS-16412](https://issues.jenkins-ci.org/browse/JENKINS-16412))
-   Fix log parsing issue for repositories defined with backslashes
    ([JENKINS-16044](https://issues.jenkins-ci.org/browse/JENKINS-16044))
-   Fix AbstractCvs instantiation error in logs for old non projectset
    builds
    ([JENKINS-15702](https://issues.jenkins-ci.org/browse/JENKINS-15702))
-   Split mail address resolver into separate plugin to prevent delays
    at the end of the build
    ([JENKINS-16389](https://issues.jenkins-ci.org/browse/JENKINS-16389))

### Version 2.7 (November 05, 2012)

-   Add functions to support CVS Tag plugin
-   Exclude Non-Head changes from changelog
    ([JENKINS-15416](https://issues.jenkins-ci.org/browse/JENKINS-15416))
-   Don't set checkout-as option on checkout command if local name is
    not over-ridden
    ([JENKINS-15132](https://issues.jenkins-ci.org/browse/JENKINS-15132))
-   Tidy up projectset parser to allow period on hostname, no port
    numbers and slashes in remote name
    ([JENKINS-15525](https://issues.jenkins-ci.org/browse/JENKINS-15525))
-   Fix mixing descriptor exception in logs
-   Force socket timeout on connection to CVS server
    ([JENKINS-13032](https://issues.jenkins-ci.org/browse/JENKINS-13032))

### Version 2.6 (September 22, 2012)

-   Fix potential NPE when cleaning up workspace following a
    checkout/update
-   Parse changelog generated from old versions of the CVS plugin
    properly
    ([JENKINS-14711](https://issues.jenkins-ci.org/browse/JENKINS-14711))
-   Add support for check-out and dynamic parse of Eclipse Projectset
    (psf) files
-   Allow configuration of username and passwords for CVSROOTs across
    jobs, similar to how cvspass file works on CVS Clients
    ([JENKINS-12582](https://issues.jenkins-ci.org/browse/JENKINS-12582))
-   Change parsing of CVS Rlog output from REGEXP to Token Based to
    improve efficiency and handle varied input
    ([JENKINS-14163](https://issues.jenkins-ci.org/browse/JENKINS-14163),
    [JENKINS-14293](https://issues.jenkins-ci.org/browse/JENKINS-14293))
-   Explicitly specify encoding for reading and writing changelog and
    temporary files
    ([JENKINS-4633](https://issues.jenkins-ci.org/browse/JENKINS-4633),
    [JENKINS-14678](https://issues.jenkins-ci.org/browse/JENKINS-14678))
-   Correct link to ViewCVS/ViewVC repository browser from changelog
    lists
    ([JENKINS-14343](https://issues.jenkins-ci.org/browse/JENKINS-14343))
-   Prevent StackOverflowException when comparing changesets
    ([JENKINS-13959](https://issues.jenkins-ci.org/browse/JENKINS-13959))
-   Perform variable expansion on module names for Core CVS (non
    Projectset) modules
    ([JENKINS-13186](https://issues.jenkins-ci.org/browse/JENKINS-13186))
-   Add OpenGrok as a repository browser
-   Add job parameter for listing CVS branches and tags for a given
    module
    ([JENKINS-9311](https://issues.jenkins-ci.org/browse/JENKINS-9311))

### Version 2.5 (August 1, 2012)

-   Build tagging allows direct creation of a branch rather than a tag
    ([JENKINS-2460](https://issues.jenkins-ci.org/browse/JENKINS-2460))
-   Checking out a non head location does not try and use sticky dates
    ([JENKINS-13789](https://issues.jenkins-ci.org/browse/JENKINS-13789))
-   Checking out a submodule, or a module into a subdirectory no longer
    causes CVS to throw an exception
    ([JENKINS-13264](https://issues.jenkins-ci.org/browse/JENKINS-13264))
-   Checkout/update no longer leaves the workspace looking like it needs
    updated before commit/build actions
    ([JENKINS-13734](https://issues.jenkins-ci.org/browse/JENKINS-13734))
-   SSH authentication file path separators are changed to match local
    system requirements when moving between slaves/host
    ([JENKINS-13764](https://issues.jenkins-ci.org/browse/JENKINS-13764))
-   Legacy mode can now be disabled properly - regression in Version 2.4
    ([JENKINS-14141](https://issues.jenkins-ci.org/browse/JENKINS-14141))

### Version 2.4 (June 3, 2012)

-   Branch/Tag/Head is specified above module level in configuration to
    save entering/changing the name in multiple locations
    ([JENKINS-12598](https://issues.jenkins-ci.org/browse/JENKINS-12598))
-   Enabling 'use head if not found' now detects changes and creates
    change-logs properly
    ([JENKINS-12104](https://issues.jenkins-ci.org/browse/JENKINS-12104))
-   Polling and change-logs now work correctly on branch and tag modules
    ([JENKINS-13277](https://issues.jenkins-ci.org/browse/JENKINS-13277))
-   Post build tagging no longer throws exception during execution
    ([JENKINS-13439](https://issues.jenkins-ci.org/browse/JENKINS-13439))

### Version 2.3 (April 12, 2012)

-   Implemented [Hierarchical projects
    support](https://wiki.jenkins.io/display/JENKINS/Hierarchical+projects+support)

### Version 2.2 (March 26, 2012)

-   Timezones in CVS commands are now formatted numerically (+XXXX
    rather then GMT/EST/CEST etc)
    ([JENKINS-12573](https://issues.jenkins-ci.org/browse/JENKINS-12573))
-   Perform variable expansion on known hosts and private key fields
-   Fix issue writing local files marked as read only in the repository

### Version 2.1 (March 17, 2012)

Fixes various issues introduced with Version 2.0:

-   Module names are always explicitly specified in checkout and update
    command to prevent checking out of all modules
    ([JENKINS-12595](https://issues.jenkins-ci.org/browse/JENKINS-12595),
    [JENKINS-12581](https://issues.jenkins-ci.org/browse/JENKINS-12581))
-   Close connections to CVS servers on finishing action
    ([JENKINS-12612](https://issues.jenkins-ci.org/browse/JENKINS-12612))
-   Date handling for old changelog files and some CVS servers
    ([JENKINS-13017](https://issues.jenkins-ci.org/browse/JENKINS-13017),
    [JENKINS-12573](https://issues.jenkins-ci.org/browse/JENKINS-12573),
    [JENKINS-12586](https://issues.jenkins-ci.org/browse/JENKINS-12586))
-   Concurrent Modification Exception for CVS
    ([JENKINS-12987](https://issues.jenkins-ci.org/browse/JENKINS-12987))
-   Password file "${user.home}/.cvspass" is ignored under some
    conditions
    ([JENKINS-12582](https://issues.jenkins-ci.org/browse/JENKINS-12582),
    migration issue only)

Features added in this release:

-   Add in EXT (SSH) support for cvsclient including public key
    authentication
    ([JENKINS-4687](https://issues.jenkins-ci.org/browse/JENKINS-4687))
-   Set executable file permissions as per repository state
    ([JENKINS-12628](https://issues.jenkins-ci.org/browse/JENKINS-12628))
-   Allow clean checkout if update fails
    ([JENKINS-753](https://issues.jenkins-ci.org/browse/JENKINS-753))

### Version 2.0 (Jan 28, 2012)

-   Allowing multiple repositories (CVSROOTs) in a single checkout
    ([JENKINS-2638](https://issues.jenkins-ci.org/browse/JENKINS-2638))
-   Allow modules from different locations (branch, tag or head) in a
    single checkout
    ([JENKINS-768](https://issues.jenkins-ci.org/browse/JENKINS-768),
    [JENKINS-6812](https://issues.jenkins-ci.org/browse/JENKINS-6812))
-   Use a java CVS library rather than require a system binary
    ([JENKINS-49](https://issues.jenkins-ci.org/browse/JENKINS-49),
    [JENKINS-3848](https://issues.jenkins-ci.org/browse/JENKINS-3848))
-   Use the CVS rlog command for polling and the rtag command for
    tagging (rather than log and tag)
-   Fix handling of files with non ASCII characters in the filenames
-   Encrypt CVS passwords so they're not visible to other users
-   Workspace is no longer required for polling
-   Changes in the repository state (deleted files) are picked up
    properly
-   Variable references can be used in branch and tag names

### Version 1.6 (Aug 17, 2011)

-   Location of cvspass and cvs.exe can now contain environment variable
    reference.
    ([report](http://jenkins.361315.n4.nabble.com/cvspass-location-on-slave-td3746864.html))

### Version 1.5 (Jul 25, 2011)

-   Fixed handling of file name with '&'
    ([JENKINS-10241](https://issues.jenkins-ci.org/browse/JENKINS-10241))
-   Added a switch to support -f
    ([JENKINS-9953](https://issues.jenkins-ci.org/browse/JENKINS-9953))

### Version 1.4 (May 27, 2011)

-   Improved the handling of large changelog

### Version 1.3 (Feb 14, 2011)

-   Translation update (Chinese)

### Version 1.2 (Jul 20, 2010)

-   Fix NPE in "tag all upstream builds" feature.
    ([JENKINS-4374](https://issues.jenkins-ci.org/browse/JENKINS-4374))
-   Avoid file handle leak if there are errors reading CVS/Entries
    files.

### Version 1.1 (Mar 25, 2010)

-   Improved the debug switch behavior to cover "cvs update"
    ([report](http://n4.nabble.com/cvs-polling-fails-after-2nd-build-td1595867.html#a1599322))
-   Space in the module name breaks after-the-fact tagging
    ([JENKINS-4961](https://issues.jenkins-ci.org/browse/JENKINS-4961))

### Version 1.0 (Jan 13, 2010)

-   Fixed "tried to access method
    hudson.scm.ChangeLogSet$Entry.setParent(Lhudson/scm/ChangeLogSet;)V
    from class hudson.scm.CVSChangeLogSet"
    ([JENKINS-5251](https://issues.jenkins-ci.org/browse/JENKINS-5251))

### Version 0.1 (Dec 24, 2009)

-   Initial version, split from the core. The CVS functionality has been
    in the core for several years.
