Incomplete test bags
--------------------

The sha's of the bags in the directory `vault` are most likely not valid.
There might be other hic-ups with bag-store commands.
The REST requests required for indexing should be OK.


Copy the test bags on the local VM 
----------------------------------

```
cd /home/vagrant/test-data/
sudo su
mkdir /data/bag-stores/pdbs/ab/
mkdir /data/bag-stores/pdbs/ab/123456789012345678901234567890/
mkdir /data/bag-stores/pdbs/40/
mkdir /data/bag-stores/pdbs/40/594b6d83784260b96b13b57beadf7c/ 

# bag UUID: 40594b6d-8378-4260-b96b-13b57beadf7c space travel 
cp -r 9da0541a-d2c8-432e-8129-979a9830b427 /data/bag-stores/pdbs/ab/123456789012345678901234567890/

# bag UUID: ab123456-7890-1234-5678-901234567890 initial PDBS problems
cp -r 1afcc4e9-2130-46cc-8faf-2663e199b218 /data/bag-stores/pdbs/40/594b6d83784260b96b13b57beadf7c/

find /data/bag-stores/ -name '.*'
```

Drop the hidden files (if any) and run `easy-update-solr4files-index init`