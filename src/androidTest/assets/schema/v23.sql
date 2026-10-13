-- What aCal ran to make a new database at version 23 (as of commit 5c0f4f60).
-- One statement to a line.
CREATE TABLE dav_server (_id INTEGER PRIMARY KEY AUTOINCREMENT,friendly_name TEXT,supplied_user_url TEXT,supplied_path TEXT,use_ssl BOOLEAN,hostname TEXT,port INTEGER,principal_path TEXT,auth_type INTEGER,username TEXT,password TEXT,has_srv BOOLEAN,has_wellknown BOOLEAN,has_caldav BOOLEAN,has_multiget BOOLEAN,has_sync BOOLEAN,active BOOLEAN,last_checked DATETIME,use_advanced BOOLEAN,prepared_config TEXT,UNIQUE(use_ssl,hostname,port,principal_path,username));
CREATE TABLE dav_path_set (_id INTEGER PRIMARY KEY AUTOINCREMENT,server_id INTEGER REFERENCES dav_server(_id),set_type INT,path TEXT,collection_tag TEXT,last_checked DATETIME,needs_sync BOOLEAN,UNIQUE(server_id, set_type, path));
CREATE TABLE dav_collection (_id INTEGER PRIMARY KEY AUTOINCREMENT,server_id INTEGER REFERENCES dav_server(_id),collection_path TEXT,displayname TEXT,holds_events BOOLEAN,holds_tasks BOOLEAN,holds_journal BOOLEAN,holds_addressbook BOOLEAN,active_events BOOLEAN,active_tasks BOOLEAN,active_journal BOOLEAN,active_addressbook BOOLEAN,last_synchronised DATETIME,needs_sync BOOLEAN,sync_token TEXT,collection_tag TEXT,default_timezone TEXT,colour TEXT,use_alarms BOOLEAN,max_sync_age_wifi INTEGER,max_sync_age_3g INTEGER,is_writable BOOLEAN,is_visible BOOLEAN,sync_metadata BOOLEAN,manually_added BOOLEAN,UNIQUE(server_id,collection_path));
CREATE TABLE dav_resource (_id INTEGER PRIMARY KEY AUTOINCREMENT,collection_id INTEGER REFERENCES dav_collection(_id),name TEXT,etag TEXT,last_modified DATETIME,content_type TEXT,data BLOB,needs_sync BOOLEAN,earliest_start NUMERIC,latest_end NUMERIC,effective_type TEXT,UNIQUE(collection_id,name));
CREATE TABLE pending_change (_id INTEGER PRIMARY KEY AUTOINCREMENT,collection_id INTEGER REFERENCES dav_collection(_id),resource_id INTEGER REFERENCES dav_resource(_id),old_data BLOB,new_data BLOB,uid TEXT,UNIQUE(collection_id,resource_id));
CREATE UNIQUE INDEX event_select_idx ON dav_resource ( effective_type, collection_id, latest_end, _id );
CREATE UNIQUE INDEX todo_select_idx ON dav_resource ( effective_type, collection_id, _id );
CREATE TABLE event_cache (_id INTEGER PRIMARY KEY AUTOINCREMENT,resource_id INTEGER REFERENCES dav_resource(_id),resource_type TEXT,recurrence_id TEXT,collection_id NUMERIC,summary TEXT,location TEXT,dtstart NUMERIC,dtend NUMERIC,completed BOOLEAN,dtstartfloat BOOLEAN,dtendfloat BOOLEAN,completedfloat BOOLEAN,flags INTEGER);
CREATE TABLE event_cache_meta (_id INTEGER PRIMARY KEY AUTOINCREMENT,dtstart NUMERIC,dtend NUMERIC,count INTEGER,closed BOOLEAN);
INSERT INTO event_cache_meta (dtstart, dtend, count, closed) VALUES(0,0,0,0);
CREATE TABLE show_upcoming_widget_data (_id INTEGER PRIMARY KEY AUTOINCREMENT,resource_id INTEGER REFERENCES dav_resource(_id),etag TEXT,colour INTEGER,dtstart NUMERIC,dtend NUMERIC,summary TEXT);
CREATE TABLE alarms (_id INTEGER PRIMARY KEY AUTOINCREMENT,ttf NUMERIC,base_ttf NUMERIC,rid NUMERIC,rrid TEXT,state NUMERIC, blob TEXT);
CREATE TABLE alarm_meta (_id INTEGER PRIMARY KEY AUTOINCREMENT,closed BOOLEAN);
INSERT INTO alarm_meta (closed) VALUES(0);
CREATE TABLE timezone (_id INTEGER PRIMARY KEY AUTOINCREMENT,tzid TEXT,default_name TEXT,last_modified NUMERIC,zone_data BLOB, UNIQUE(tzid));
CREATE TABLE timezone_name (_id INTEGER PRIMARY KEY AUTOINCREMENT,tzid TEXT REFERENCES timezone(tzid),tzname TEXT,locale TEXT,UNIQUE(tzid,locale));
CREATE TABLE timezone_alias (alias TEXT PRIMARY KEY ,tzid TEXT REFERENCES timezone(tzid));
