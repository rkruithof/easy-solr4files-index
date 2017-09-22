easy-update-solr4files-index
===========
[![Build Status](https://travis-ci.org/DANS-KNAW/easy-update-solr4files-index.png?branch=master)](https://travis-ci.org/DANS-KNAW/easy-update-solr4files-index)


SYNOPSIS
--------

  easy-update-solr4files-index {update|delete} [-s <bag-store>] <uuid>
  easy-update-solr4files-index {init} <bag-store>
  easy-update-solr4files-index run-service

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

      --help      Show help message
      --version   Show version of this program

    Subcommand: update - Update accessible files of a bag in the SOLR index
      -s, --bag-store  <arg>   Name of the bag store (default = pdbs)
          --help               Show help message
    
     trailing arguments:
      bag-uuid (required)
    ---
    
    Subcommand: delete - Delete documents from the SOLR index; '*:*' deletes all, 'id:<UUID>*' deletes a bag
          --help   Show help message
    
     trailing arguments:
      solr-query (required)
    ---
    
    Subcommand: init - Rebuild the SOLR index from scratch for active bags in one or all store(s)
          --help   Show help message
    
     trailing arguments:
      bag-store (not required)
    ---
    
    Subcommand: run-service - Starts EASY Update Solr4files Index as a daemon that services HTTP requests
          --help   Show help message
    ---

EXAMPLES
--------

    easy-update-solr4files-index update 9da0541a-d2c8-432e-8129-979a9830b427


INSTALLATION AND CONFIGURATION
------------------------------


1. Unzip the tarball to a directory of your choice, typically `/usr/local/`
2. A new directory called easy-update-solr4files-index-<version> will be created
3. Add the command script to your `PATH` environment variable by creating a symbolic link to it from a directory that is
   on the path, e.g. 
   
        ln -s /usr/local/easy-update-solr4files-index-<version>/bin/easy-update-solr4files-index /usr/bin



General configuration settings can be set in `cfg/application.properties` and logging can be configured
in `cfg/logback.xml`. The available settings are explained in comments in aforementioned files.


BUILDING FROM SOURCE
--------------------

Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher

Steps:

        git clone https://github.com/DANS-KNAW/easy-update-solr4files-index.git
        cd easy-update-solr4files-index
        mvn install
