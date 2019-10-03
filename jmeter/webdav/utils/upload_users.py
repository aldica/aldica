#
# Simple convenience script...
#
# Used for generating a number of Alfresco users to be used in the load test
# This script will output a CSV file which can be consumed by JMeter
#

import requests
from requests.auth import HTTPBasicAuth

from config import BASE_URL, ADMIN_USER, ADMIN_PWD


URL = BASE_URL + '/alfresco/s/api/people'
N_USERS = 50
SITE_SHORT_NAME = 'swsdp'
GROUP = 'SiteCollaborator'

client = requests.Session()
auth = HTTPBasicAuth(ADMIN_USER, ADMIN_PWD)
users = [
    ('user{}'.format(str(i)), 'dummyLastname') for i in range(N_USERS)
]


def upload_user(user: tuple):
    payload = {
        'userName': user[0].lower(),
        'firstName': user[0],
        'lastName': user[1],
        'email': user[0].lower() + '@alfresco.example.org',
        'password': user[0].lower(),
        'groups': ['GROUP_site_' + SITE_SHORT_NAME + '_' + GROUP] if SITE_SHORT_NAME else []
    }
    r = client.post(URL, json=payload, auth=auth)
    return r


# Not currently used...
def delete_user(user: tuple):
    r = client.delete('{}/{}'.format(URL, user[0].lower()),
                      auth=auth)


with open('users.csv', 'w') as fp:
    for user in users:
        r = upload_user(user)
        print(r)
        fp.write(user[0].lower() + '\n')
