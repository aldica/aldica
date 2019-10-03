#
# Simple convenience script...
#
# Used for generating the folder hierarchy to be used in the load test
# This script will output a CSV file which can be consumed by JMeter
#

import requests
from requests.auth import HTTPBasicAuth
from config import BASE_URL, ADMIN_USER, ADMIN_PWD


N = 10
URL = BASE_URL + '/alfresco/webdav/Shared/'

auth = HTTPBasicAuth(ADMIN_USER, ADMIN_PWD)
with open('folders.csv', 'w') as fp:
    for i in range(N):
        r = requests.request('MKCOL', url=URL + 'f' + str(i), auth=auth)
        print(r.status_code)
        for j in range(N):
            r = requests.request('MKCOL', url=URL + 'f' + str(i) + '/f' + str(j), auth=auth)
            print(r.status_code)
            fp.write('{},{}\n'.format(i, j))

