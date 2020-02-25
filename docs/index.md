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

EXAMPLES
--------

Using the command line to update a single bag in the store `pdbs` respective (re)index all bags in all stores.

    easy-solr4files-index -s pdbs update 9da0541a-d2c8-432e-8129-979a9830b427
    easy-solr4files-index init

Using the rest interface to delete a bag from the index respective (re)index all bags in one store.

    curl -X DELETE 'http://test.dans.knaw.nl:20150/fileindex/?q=easy_dataset_id:ef425828-e4ae-4d58-bf6a-c89cd46df61c'
    curl -X POST 'http://test.dans.knaw.nl:20150/fileindex/init/pdbs'

Retrieve the second page of PDF and text files containing one of the words `foo` or `bar`.
See the [security advice](#security-advice) for the URL part preceding the `?`.

    curl 'http://test.dans.knaw.nl:20150/filesearch?text=foo+bar&file_mime_type=application/pdf&file_mime_type=text&skip=10&limit=10'


INSTALLATION AND CONFIGURATION
------------------------------

### Prerequisites

* [easy-bag-store](https://github.com/DANS-KNAW/easy-bag-store/)
* [dans.solr](https://github.com/DANS-KNAW/dans.solr)
* [dans.easy-ldap-dir](https://github.com/DANS-KNAW/dans.easy-ldap-dir)
* A [Solr core](src/main/assembly/dist/install/fileitems),
  installed for example with with [vagrant.yml](src/main/ansible/vagrant.yml).
  Thus a web-ui comes available for administrators with `http://localhost:8983/solr/#/fileitems/query`.
  A command line example:

        curl 'http://test.dans.knaw.nl:8983/solr/fileitems/query?q=*&fl=*'

### Steps

1. Unzip the tarball to a directory of your choice, typically `/usr/local/`
2. A new directory called easy-solr4files-index-<version> will be created
3. Add the command script to your `PATH` environment variable by creating a symbolic link to it from a directory that is
   on the path, e.g. 
   
        ln -s /usr/local/easy-solr4files-index-<version>/bin/easy-solr4files-index /usr/bin

General configuration settings can be set in `cfg/application.properties` and logging can be configured
in `cfg/logback.xml`. The available settings are explained in comments in aforementioned files.


### Security advice

Keep the admin interface (command line and `fileindex` servlet)
and any other direct access to solr, the bag store and ldap behind a firewall.
Only expose the `filesearch` servlet through a proxy, map for example:
`http://easy.dans.knaw.nl/files/search` to `http://localhost:20150/filesearch` 


BUILDING FROM SOURCE
--------------------

Prerequisites:

* RPM
* Java 8 or higher
* Maven 3.3.3 or higher

Steps:

        git clone https://github.com/DANS-KNAW/easy-solr4files-index.git
        cd easy-solr4files-index
        mvn install
