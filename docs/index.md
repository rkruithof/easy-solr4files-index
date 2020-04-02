MANUAL
======
[![Build Status](https://travis-ci.org/DANS-KNAW/easy-solr4files-index.png?branch=master)](https://travis-ci.org/DANS-KNAW/easy-solr4files-index)


SYNOPSIS
--------

    easy-solr4files-index update [-s <bag-store>] <uuid>
    easy-solr4files-index init <bag-store>
    easy-solr4files-index delete <solr-query>
    easy-solr4files-index run-service
    
    Some examples of standard solr queries for the delete command:
    
      everything:            '*:*'
      all bags of one store: 'easy_dataset_store_id:pdbs'
      a bag:                 'easy_dataset_id:ef425828-e4ae-4d58-bf6a-c89cd46df61c'
      a folder in a bag:     'id:ef425828-e4ae-4d58-bf6a-c89cd46df61c/data/files/Documents/*'


DESCRIPTION
-----------

Update the EASY SOLR for Files Index with file data from a bag-store.

File content is indexed together with some file metadata as well as dataset metadata.
Files are only indexed if `easy_file_accessible_to` gets a value 
`anonymous`, `known`, `restrictedGroup` or `restrictedRequest`.
If the value is not provided at file level by `metadata/files.xml`,
a default is derived from `<ddm:profile><ddm:accessRights>` in `metadata/dataset.xml`.


ARGUMENTS
---------

    Options:

      -h, --help      Show help message
      -v, --version   Show version of this program

    Subcommand: update - Update accessible files of a bag in the SOLR index
      -s, --bag-store  <arg>   Name of the bag store (default = pdbs)
      -h, --help               Show help message
    
     trailing arguments:
      bag-uuid (required)
    ---
    
    Subcommand: delete - Delete documents from the SOLR index
      -h, --help   Show help message
    
     trailing arguments:
      solr-query (required)
    ---
    
    Subcommand: init - Rebuild the SOLR index from scratch for active bags in one or all store(s)
      -h, --help   Show help message
    
     trailing arguments:
      bag-store (not required)
    ---
    
    Subcommand: run-service - Starts EASY Solr4files Index as a daemon that services HTTP requests
      -h, --help   Show help message
    ---

INSTALLATION AND CONFIGURATION
------------------------------
The preferred way of install this module is using the RPM package. This will install the binaries to
`/opt/dans.knaw.nl/easy-deposit-api` and the configuration files to `/etc/opt/dans.knaw.nl/easy-deposit-api`.

To install the module on systems that do not support RPM, you can copy and unarchive the tarball to the target host.
You will have to take care of placing the files in the correct locations for your system yourself. For instructions
on building the tarball, see next section.

### Security advice

Keep the admin interface (command line and `fileindex` servlet)
and any other direct access to solr, the bag store and ldap behind a firewall.
Only expose the `filesearch` servlet through a proxy, map for example:
`http://easy.dans.knaw.nl/files/search` to `http://localhost:20150/filesearch` 


BUILDING FROM SOURCE
--------------------

Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher
* RPM 

Steps:

    git clone https://github.com/DANS-KNAW/easy-deposit-api.git
    cd easy-deposit-api
    mvn install

If the `rpm` executable is found at `/usr/local/bin/rpm`, the build profile that includes the RPM 
packaging will be activated. If `rpm` is available, but at a different path, then activate it by using
Maven's `-P` switch: `mvn -Pprm install`.

Alternatively, to build the tarball execute:

    mvn clean install assembly:single
