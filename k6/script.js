import http from 'k6/http';
import encoding from 'k6/encoding';
import { Counter, Rate, Trend } from 'k6/metrics';
import { sleep, group } from 'k6';

const loginFails = new Rate('Logins failed');
const badRequests = new Rate('Bad requests');
const expectedNodeNotFound = new Rate('Expected node not found');
const permissionDenied = new Rate('Permission denied failures');
const nameConflicts = new Rate('Name conflicts');
const integrityViolations = new Rate('Data integrity violation');
const unspecificFails = new Rate('Non-specific failures');

const createdNodes = new Counter('(Directly) Created nodes');
const updatedNodes = new Counter('(Directly) Updated nodes');

const users = JSON.parse(open('./users.json'));
const hosts = JSON.parse(open('./hosts.json'));

const contents = [
  open('./content1', 'b'),
  open('./content2', 'b'),
  open('./content3', 'b'),
  open('./content4', 'b'),
  open('./content5', 'b'),
  open('./content6', 'b'),
  open('./content7', 'b'),
  open('./content8', 'b'),
  open('./content9', 'b'),
  open('./content10', 'b'),
  open('./content11', 'b'),
  open('./content12', 'b'),
  open('./content13', 'b'),
  open('./content14', 'b'),
  open('./content15', 'b'),
  open('./content16', 'b')
];

export let options = {
	thresholds: {
    'Logins failed': [{
      threshold:'rate < 0.01',
      abortOnFail : true,
      delayAbortEval : '5s'
    }],
    'Bad requests': [{
      threshold:'rate < 0.01',
      abortOnFail : true,
      delayAbortEval : '15s'
    }],
    'Expected node not found': [{
      threshold:'rate < 0.05',
      abortOnFail : true,
      delayAbortEval : '15s'
    }],
    'Permission denied failures': ['rate < 0.01'],
    'Non-specific failures': ['rate < 0.05'],
    'Name conflicts': ['rate < 0.01'],
    'Data integrity violation': ['rate < 0.01']
  },
  stages: [
    { duration: "2m", target: 40 },
    { duration: "26m", target: 40 },
    { duration: "2m", target: 0 }
  ]
};

function createSimpleUuid()
{
  // version 4, variant 1 UUID using current time as seed
  let dt = new Date().getTime();
  let uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
      let r = (dt + Math.random()*16)%16 | 0;
      dt = Math.floor(dt/16);
      return (c=='x' ? r :((r & 0x3) | 0x8)).toString(16);
  });
  return uuid;
}

function getUserIds()
{
  return Object.keys(users);
}

function getHostIds()
{
  return hosts.fineGrained ? Object.keys(hosts.fineGrained) : [];
}

function getUserAttribute(context, attributeName)
{
  let userId = context.userId;
  let host = context.host;

  let defaultUserData = users[userId].default || {};
  let result = defaultUserData[attributeName];
  if (host)
  {
    let hostSpecificUserData = (users[userId].fineGrained || {})[host] || {};
    result = hostSpecificUserData[attributeName] || result;
  }
  return result;
}

function getHostAttribute(context, attributeName)
{
  let host = context.host;

  let defaultData = hosts.default;
  let result = defaultData[attributeName];
  if (host)
  {
    let hostData = hosts.fineGrained[host] || {};
    result = hostData[attributeName] || result;
  }
  return result;
}

function buildUrl(context, api, version, opUrl, tokens, params)
{
  let proto = getHostAttribute(context, 'protocol') || 'http';
  let host = getHostAttribute(context, 'host') || 'localhost';
  let port = getHostAttribute(context, 'port') || (proto === 'https' ? 443 : 80);
  let rootContext = getHostAttribute(context, 'rootContext') || '/alfresco';

  let url = proto + '://' + host;
  if ((proto === 'http' && port !== 80) || (proto === 'https' && port !== 443))
  {
    url += ':' + port;
  }
  url += rootContext;
  url += '/api/-default-/public/';
  url += api;
  url += '/versions/';
  url += version || 1;
  url += '/';
  url += opUrl;

  if (tokens)
  {
    for (let tokenKey in tokens)
    {
      if (tokens.hasOwnProperty(tokenKey))
      {
        url = url.replace('{' + tokenKey + '}', tokens[tokenKey]);
      }
    }
  }

  if (params)
  {
    for (let paramKey in params)
    {
      if (params.hasOwnProperty(paramKey) && params[paramKey] !== undefined && params[paramKey] !== null)
      {
        if (url.indexOf('?') === -1)
        {
          url += '?';
        }else{
          url += '&';
        }

        url += encodeURIComponent(paramKey) + '='

        if (params[paramKey].length && !(typeof params[paramKey] === 'string' || params[paramKey] instanceof String))
        {
          for(let idx = 0; idx < params[paramKey].length; idx++)
          {
            if (idx > 0)
            {
              url += ',';
            }
            url += encodeURIComponent(params[paramKey][idx]);
          }
        }
        else
        {
          url += encodeURIComponent(params[paramKey]);
        }
      }
    }
  }

  return url;
}

function enhanceTags(baseTags, additionalTags)
{
  let effectiveTags = baseTags ? JSON.parse(JSON.stringify(baseTags)) : {};
  for (let key in additionalTags)
  {
    if (additionalTags.hasOwnProperty(key))
    {
      effectiveTags[key] = additionalTags[key];
    }
  }
  return effectiveTags;
}

function buildEffectiveRequestHeaders(context, headers, body)
{
  let effectiveHeaders = headers ? JSON.parse(JSON.stringify(headers)) : {};

  if (!effectiveHeaders.Authorization)
  {
    if (context.ticket)
    {
      effectiveHeaders.Authorization = 'BASIC ' + encoding.b64encode(context.ticket);
    }
    else
    {
      let password = getUserAttribute(context, 'password');
      effectiveHeaders.Authorization = 'BASIC ' + encoding.b64encode(context.userId + ':' + password);
    }
  }

  if (!effectiveHeaders['Content-Type'] && body)
  {
    if (typeof body !== 'string' && !(body instanceof String))
    {
      let containsFileData = false;
      for (let bodyKey in body)
      {
        if (body.hasOwnProperty(bodyKey) && body[bodyKey])
        {
          containsFileData = containsFileData || body[bodyKey].filename;
        }
      }

      if (!containsFileData)
      {
        effectiveHeaders['Content-Type'] = 'application/x-www-form-urlencoded';
      }
    }
  }

  return effectiveHeaders;
}

function post(context, api, version, opUrl, tokens, params, body, headers, tags, customResponseType, cb)
{
  let effectiveTags = enhanceTags(tags, {
    alfrescoApiCatalog: api,
    alfrescoApiOperation: opUrl
  });
  let effectiveHeaders = buildEffectiveRequestHeaders(context, headers, body);
  let url = buildUrl(context, api, version, opUrl, tokens, params);

  let res = http.post(url, body, {
    headers : effectiveHeaders,
    tags : effectiveTags,
    responseType : customResponseType || 'text',
    redirects : 0
  });

  if (typeof cb === 'function')
  {
    cb(res, effectiveTags);
  }

  return res;
}

function put(context, api, version, opUrl, tokens, params, body, headers, tags, customResponseType, cb)
{
  let effectiveTags = enhanceTags(tags, {
    alfrescoApiCatalog: api,
    alfrescoApiOperation: opUrl
  });
  let effectiveHeaders = buildEffectiveRequestHeaders(context, headers, body);
  let url = buildUrl(context, api, version, opUrl, tokens, params);

  let res = http.put(url, body, {
    headers : effectiveHeaders,
    tags : effectiveTags,
    responseType : customResponseType || 'text',
    redirects : 0
  });

  if (typeof cb === 'function')
  {
    cb(res, effectiveTags);
  }

  return res;
}

function get(context, api, version, opUrl, tokens, params, headers, tags, customResponseType, cb)
{
  let effectiveTags = enhanceTags(tags, {
    alfrescoApiCatalog: api,
    alfrescoApiOperation: opUrl
  });
  let effectiveHeaders = buildEffectiveRequestHeaders(context, headers);
  let url = buildUrl(context, api, version, opUrl, tokens, params);

  let res = http.get(url, {
    headers : effectiveHeaders,
    tags : effectiveTags,
    responseType : customResponseType || 'text',
    redirects : 0
  });

  if (typeof cb === 'function')
  {
    cb(res, effectiveTags);
  }

  return res;
}

function login(context, headers, tags)
{
  let body = {
    userId : context.userId,
    password : getUserAttribute(context, 'password')
  };

  let res = post(context, 'authentication', 1, 'tickets', null, null, JSON.stringify(body), headers, tags, null, (r, t) => {
    badRequests.add(r.status === 400, t);
    loginFails.add(r.status === 400 || res.status === 403 || res.status === 501, t);
    unspecificFails.add(r.status >= 400 && !(res.status === 400 || res.status === 403 || res.status === 501), t);
  });

  if (res.status === 201)
  {
    let resBody = JSON.parse(res.body);
    context.ticket = resBody.entry.id;
  }
}

const getNodeTimeTTFB = new Trend('Time to get node (TTFB)', true);
const getNodeTimeRes = new Trend('Time to get node (res)', true);
const getNodeTimeDur = new Trend('Time to get node (full)', true);

function loadNode(context, nodeId, relativePath, include, fields, expectPresence, headers, tags)
{
  let res = get(context, 'alfresco', 1, 'nodes/{nodeId}', {
    nodeId: nodeId || '-root-'
  }, {
    relativePath: relativePath,
    include: include,
    fields: fields
  }, headers, tags, null, (r, t) => {
    badRequests.add(r.status === 400, t);
    expectedNodeNotFound.add(expectPresence && r.status === 404, t);
    permissionDenied.add(r.status === 403, t);
    unspecificFails.add(r.status >= 400 && !(r.status === 400 || r.status === 401 || r.status === 403 || r.status === 404), t);
  });

  if (res.status === 200)
  {
    getNodeTimeTTFB.add(res.timings.waiting);
    getNodeTimeRes.add(res.timings.waiting + res.timings.receiving);
    getNodeTimeDur.add(res.timings.duration);

    let node = JSON.parse(res.body).entry;
    return node;
  }
}

const loadChildrenTimeTTFB = new Trend('Time to load node children (TTFB)', true);
const loadChildrenTimeRes = new Trend('Time to load node children (res)', true);
const loadChildrenTimeDur = new Trend('Time to load node children (full)', true);

function loadChildren(context, nodeId, relativePath, where, skipCount, maxItems, orderBy, include, fields, headers, tags)
{
  let res = get(context, 'alfresco', 1, 'nodes/{nodeId}/children', {
    nodeId: nodeId || '-root-'
  }, {
    relativePath: relativePath,
    where: where,
    skipCount: skipCount || 0,
    maxItems: maxItems || 100,
    orderBy: orderBy || 'name ASC',
    include: include,
    fields: fields
  }, headers, tags, null, (r, t) => {
    badRequests.add(r.status === 400, t);
    expectedNodeNotFound.add(r.status === 404, t);
    permissionDenied.add(r.status === 403, t);
    unspecificFails.add(r.status >= 400 && !(r.status === 400 || r.status === 401 || r.status === 403 || r.status === 404), t);
  });

  if (res.status === 200)
  {
    loadChildrenTimeTTFB.add(res.timings.waiting);
    loadChildrenTimeRes.add(res.timings.waiting + res.timings.receiving);
    loadChildrenTimeDur.add(res.timings.duration);

    let node = JSON.parse(res.body).list.entries;
    return node;
  }
}

const queryTimeTTFB = new Trend('Time to query nodes (TTFB)', true);
const queryTimeRes = new Trend('Time to query nodes (res)', true);
const queryTimeDur = new Trend('Time to query nodes (full)', true);

function searchNodes(context, queryDefinition, skip, limit, include, fields, headers, tags)
{
  let effectiveSearchQueryDefinition = JSON.parse(JSON.stringify(queryDefinition));
  // include / fields can also be in the definition already
  // supported as params primarily for API consistency with loadNode
  if (include)
  {
    effectiveSearchQueryDefinition.include = include;
  }
  if (fields)
  {
    effectiveSearchQueryDefinition.fields = fields;
  }
  if (skip || limit)
  {
    effectiveSearchQueryDefinition.paging = {
      skipCount: skip,
      maxItems: limit
    };
  }

  let res = post(context, 'search', 1, 'search', null, null, JSON.stringify(effectiveSearchQueryDefinition), headers, tags, null, (r, t) => {
    badRequests.add(r.status === 400, t);
    unspecificFails.add(r.status >= 400 && !(r.status === 400), t);
  });

  if (res.status === 200)
  {
    queryTimeTTFB.add(res.timings.waiting);
    queryTimeRes.add(res.timings.waiting + res.timings.receiving);
    queryTimeDur.add(res.timings.duration);

    let resultList = JSON.parse(res.body).list;
    return resultList;
  }
}

const contentNodeCreationTimeTTFB = new Trend('Time to create node (w content TTFB)', true);
const contentLessNodeCreationTimeTTFB = new Trend('Time to create node (w/o content TTFB)', true);

const contentNodeCreationTimeRes = new Trend('Time to create node (w content res)', true);
const contentLessNodeCreationTimeRes = new Trend('Time to create node (w/o content res)', true);

const contentNodeCreationTimeDur = new Trend('Time to create node (w content dur)', true);
const contentLessNodeCreationTimeDur = new Trend('Time to create node (w/o content dur)', true);

function createNode(context, nodeId, nodeTemplate, content, autoRename, include, fields, allowClash, headers, tags)
{
  let res;

  let statusCb = (r, t) => {
    badRequests.add(r.status === 400, t);
    expectedNodeNotFound.add(r.status === 404, t);
    permissionDenied.add(r.status === 403, t);
    if (!allowClash)
    {
      nameConflicts.add(r.status === 409, t);
    }
    integrityViolations.add(r.status === 422, t);
    unspecificFails.add(r.status >= 400 && !(r.status === 400 || r.status === 401 || r.status === 403 || r.status === 404 || r.status === 409|| r.status === 422), t);
  };

  if (content)
  {
    let multipartBody = {
      filedata: content,
      nodetype: nodeTemplate.nodeType || 'cm:content',
      autorename: autoRename === true
    };
    if (nodeTemplate.name)
    {
      multipartBody.name = nodeTemplate.name;
    }
    // according to API Explorer, there is no support for 'aspectNames'
    // multipart supports 'renditions', but we don't as JSON equivalent does not support it
    // multipart supports 'overwrite' (+ associated fields), but we don't (this is a createNode operation, not overwriteNode)
    // multipart supports 'relativePath', so do we
    if (nodeTemplate.relativePath)
    {
      multipartBody.relativepath = nodeTemplate.relativePath;
    }
    // map properties into form fields
    if (nodeTemplate.properties)
    {
      for (let propertyKey in nodeTemplate.properties)
      {
        if (nodeTemplate.properties.hasOwnProperty(propertyKey))
        {
          multipartBody[propertyKey] = nodeTemplate.properties[propertyKey];
        }
      }
    }

    res = post(context, 'alfresco', 1, 'nodes/{nodeId}/children', {
      nodeId: nodeId
    }, {
      include: include,
      fields: fields
    }, multipartBody, headers, tags, null, statusCb);

    if (res.status === 201)
    {
      contentNodeCreationTimeTTFB.add(res.timings.waiting);
      contentNodeCreationTimeRes.add(res.timings.waiting + res.timings.receiving);
      contentNodeCreationTimeDur.add(res.timings.duration);
    }
  }
  else
  {
    res = post(context, 'alfresco', 1, 'nodes/{nodeId}/children', {
      nodeId: nodeId
    }, {
      autoRename: autoRename,
      include: include,
      fields: fields
    }, JSON.stringify(nodeTemplate), headers, tags, null, statusCb);

    if (res.status === 201)
    {
      contentLessNodeCreationTimeTTFB.add(res.timings.waiting);
      contentLessNodeCreationTimeRes.add(res.timings.waiting + res.timings.receiving);
      contentLessNodeCreationTimeDur.add(res.timings.duration);
    }
  }

  if (res.status === 201)
  {
    createdNodes.add(1);
    let node = JSON.parse(res.body).entry;
    return node;
  }
  if (allowClash && res.status === 409)
  {
    // this is actually an invalid ID
    return '//clash//';
  }
}

const nodeUpdateTimeTTFB = new Trend('Time to update node (TTFB)', true);
const nodeUpdateTimeRes = new Trend('Time to update node (res)', true);
const nodeUpdateTimeDur = new Trend('Time to update node (dur)', true);

function updateNode(context, nodeId, updateDefinition, include, fields, headers, tags)
{
  let res = put(context, 'alfresco', 1, 'nodes/{nodeId}', {
    nodeId: nodeId
  }, {
    include: include,
    fields: fields
  }, JSON.stringify(updateDefinition), headers, tags, null, (r, t) => {
    badRequests.add(r.status === 400, t);
    expectedNodeNotFound.add(r.status === 404, t);
    permissionDenied.add(r.status === 403, t);
    nameConflicts.add(r.status === 409, t);
    integrityViolations.add(r.status === 422, t);
    unspecificFails.add(r.status >= 400 && !(r.status === 400 || r.status === 401 || r.status === 403 || r.status === 404 || r.status === 409|| r.status === 422), t);
  });

  if (res.status === 200)
  {
    nodeUpdateTimeTTFB.add(res.timings.waiting);
    nodeUpdateTimeRes.add(res.timings.waiting + res.timings.receiving);
    nodeUpdateTimeDur.add(res.timings.duration);

    updatedNodes.add(1);
    let node = JSON.parse(res.body).entry;
    return node;
  }
}

function resolveNodeId(context, baseNodeId, relativePath, expectPresence, headers, tags)
{
  let node = loadNode(context, baseNodeId, relativePath, null, null, expectPresence, headers, tags);
  if (node)
  {
    return node.id;
  }
}

function resolveOrCreateFolderPath(context, baseNodeId, relativePath, folderTemplate, headers, tags)
{
  let pathFragments = relativePath.split(/\//);

  let curNodeId = baseNodeId;
  for (let idx = 0; idx < pathFragments.length; idx++)
  {
    let nextNodeId = resolveNodeId(context, curNodeId, pathFragments[idx], false, headers, tags);
    if (!nextNodeId)
    {
      let effectiveFolderTemplate = folderTemplate ? JSON.parse(JSON.stringify(folderTemplate)) : {};
      effectiveFolderTemplate.name = pathFragments[idx];
      effectiveFolderTemplate.nodeType = effectiveFolderTemplate.nodeType || 'cm:folder';

      let nextNode = createNode(context, curNodeId, effectiveFolderTemplate, null, false, null, null, true, headers, tags);
      if (nextNode === '//clash//')
      {
        nextNodeId = resolveNodeId(context, curNodeId, pathFragments[idx], true, headers, tags);
      }
      else if (nextNode)
      {
        nextNodeId = nextNode.id;
      }

      if (!nextNodeId)
      {
        // no need trying further
        return;
      }
    }
    curNodeId = nextNodeId;
  }
  return curNodeId;
}

const y2018StartMillis = 1514764800000;

function createNewDummyContent(tags)
{
  let userIds = getUserIds();
  let context = {
    userId: userIds[Math.floor(Math.random() * userIds.length)]
  };

  group('Create content file', function(){
    let curDate = new Date();
    let datePath = curDate.getUTCFullYear() + '/' + (curDate.getUTCMonth() + 1) + '/' + curDate.getUTCDate() + '/' + curDate.getUTCHours() + '/' + curDate.getUTCMinutes();
    let relativePath = 'Shared/' + __VU + '/' + datePath;

    let effectivityFrom = new Date(y2018StartMillis + Math.floor(Math.random() * (Date.now() - y2018StartMillis)));
    let effectivityTo = new Date(effectivityFrom.getTime() + Math.floor(Math.random() * (Date.now() - effectivityFrom.getTime())));

    let contentNodeTemplate = {
      name: createSimpleUuid(),
      nodeType: 'cm:content',
      relativePath: relativePath,
      properties: {
        'cm:hits': Math.floor(Math.random() * 100),
        'cm:from': effectivityFrom.toISOString(),
        'cm:to': effectivityTo.toISOString()
      }
    };
    createNode(context, '-root-', contentNodeTemplate, http.file(contents[Math.floor(Math.random() * contents.length)]), false, null, null, false, null, tags);
  });
}

function lookupRandomRecentlyModifiedContent(context, headers, tags)
{
  // up to 4 hours in the past
  let offsetDate = new Date(Date.now() - 1000 * 60 * 60 * 4);
  let offsetISODate = offsetDate.toISOString();

  let queryDefinition = {
    query: {
      language: 'cmis',
      query: 'SELECT * FROM cmis:document WHERE cmis:lastModificationDate >= TIMESTAMP\'' + offsetISODate + '\''
    }
  };

  let results = {entries: []};
  let pageSize = 50;
  let maxOffsetPage = 10;
  while (results && results.entries.length === 0)
  {
    let skip = pageSize * Math.floor(Math.random() * maxOffsetPage);
    results = searchNodes(context, queryDefinition, skip, pageSize, null, null, headers, tags);
    if (results && results.entries.length === 0)
    {
      maxOffsetPage = Math.max(0, maxOffsetPage - 1);
    }
  }

  if (results && results.entries.length > 0)
  {
    let selectedNode = results.entries[Math.floor(Math.random() * results.entries.length)].entry;
    return selectedNode;
  }
}

function lookupRandomChildFolder(context, parentNodeId, headers, tags)
{
  let childFolders = loadChildren(context, parentNodeId, null, '(isFolder=true)', 0, 25, null, null, null, headers, tags);
  let childFolder = childFolders.length > 0 ? childFolders[Math.floor(Math.random() * childFolders.length)].entry : null;
  return childFolder;
}

function lookupRandomContentInFolderStructure(context, baseFolderId, attemptLimit, headers, tags)
{
  let contents = loadChildren(context, baseFolderId, null, '(isFile=true)', 0, 25, null, null, null, headers, tags);

  let result;
  if (contents && contents.length > 0)
  {
    result = contents[Math.floor(Math.random() * contents.length)].entry;
  }
  else
  {
    for (let i = 0; i < attemptLimit; i++)
    {
      let randomChildFolder = lookupRandomChildFolder(context, baseFolderId, headers, tags);
      if (!randomChildFolder)
      {
        break;
      }
      let contentFromChildFolder = lookupRandomContentInFolderStructure(context, randomChildFolder.id, attemptLimit, headers, tags);
      if (contentFromChildFolder)
      {
        result = contentFromChildFolder;
        break;
      }
    }
  }

  return result;
}

function updateRandomRecentlyModifiedContent(tags)
{
  let userIds = getUserIds();
  let context = {
    userId: userIds[Math.floor(Math.random() * userIds.length)]
  };
    
  group('Query, select and modify existing file', function(){
    let existingNode = lookupRandomRecentlyModifiedContent(context, null, tags);
    if (existingNode)
    {
      let effectivityFrom = new Date(y2018StartMillis + Math.floor(Math.random() * (Date.now() - y2018StartMillis)));
      let effectivityTo = new Date(effectivityFrom.getTime() + Math.floor(Math.random() * (Date.now() - effectivityFrom.getTime())));

      let updateDefinition = {
        name: createSimpleUuid(),
        properties: {
          'cm:hits': Math.floor(Math.random() * 100),
          'cm:from': effectivityFrom.toISOString(),
          'cm:to': effectivityTo.toISOString()
        }
      };

      // users take some time between retrieval + modification
      sleep(0.5 + Math.floor(Math.random() * 1.5));

      updateNode(context, existingNode.id, updateDefinition, null, null, null, tags);
    }
  });
}

function updateRandomContent(rootFolderId, tags)
{
  let userIds = getUserIds();
  let context = {
    userId: userIds[Math.floor(Math.random() * userIds.length)]
  };

  group('Lookup and modify existing file', function(){
    let existingNode = lookupRandomContentInFolderStructure(context, rootFolderId, null, tags);
    if (existingNode)
    {
      let effectivityFrom = new Date(y2018StartMillis + Math.floor(Math.random() * (Date.now() - y2018StartMillis)));
      let effectivityTo = new Date(effectivityFrom.getTime() + Math.floor(Math.random() * (Date.now() - effectivityFrom.getTime())));

      let updateDefinition = {
        name: createSimpleUuid(),
        properties: {
          'cm:hits': Math.floor(Math.random() * 100),
          'cm:from': effectivityFrom.toISOString(),
          'cm:to': effectivityTo.toISOString()
        }
      };

      // users take some time between retrieval + modification
      sleep(0.5 + Math.floor(Math.random() * 1.5));

      updateNode(context, existingNode.id, updateDefinition, null, null, null, tags);
    }
  });
}

const clusterBeforeUpdateValidationError = new Rate('Cluster node state inconsistencies before update');
const clusterAfterUpdateValidationError = new Rate('Cluster node state inconsistencies after update');

function updateRandomRecentlyModifiedContentAndVerifyInCluster(tags)
{
  group('Query, select and modify existing file with verification in cluster', function(){
    let userIds = getUserIds();
    let context = {
      userId: userIds[Math.floor(Math.random() * userIds.length)]
    };
    let context1 = {
      userId: userIds[Math.floor(Math.random() * userIds.length)],
      host: 'clusterNode' + Math.floor(1 + Math.random() * 1)
    };
    let context2 = {
      userId: userIds[Math.floor(Math.random() * userIds.length)],
      host: context1.host === 'clusterNode1' ? 'clusterNode2' : 'clusterNode1'
    };

    let existingNode = lookupRandomRecentlyModifiedContent(context, null, tags);
    if (existingNode)
    {
      let existingNode1 = loadNode(context1, existingNode.id, null, null, null, true, null, tags);
      let existingNode2 = loadNode(context2, existingNode.id, null, null, null, true, null, tags);

      let inconsistent = existingNode1.properties['cm:hits'] !== existingNode2.properties['cm:hits']
        || existingNode1.properties['cm:from'] !== existingNode2.properties['cm:from']
        || existingNode1.properties['cm:to'] !== existingNode2.properties['cm:to'];
      clusterBeforeUpdateValidationError.add(inconsistent);
      if (inconsistent)
      {
         console.log('Inconsistency found before update between ' + JSON.stringify(existingNode1) + ' and ' + JSON.stringify(existingNode2));
      }

      // users take some time between retrieval + modification
      sleep(0.5 + Math.floor(Math.random() * 1.5));
      
      let effectivityFrom = new Date(y2018StartMillis + Math.floor(Math.random() * (Date.now() - y2018StartMillis)));
      let effectivityTo = new Date(effectivityFrom.getTime() + Math.floor(Math.random() * (Date.now() - effectivityFrom.getTime())));
  
      let updateDefinition = {
        name: createSimpleUuid(),
        properties: {
          'cm:hits': Math.floor(Math.random() * 100),
          'cm:from': effectivityFrom.toISOString(),
          'cm:to': effectivityTo.toISOString()
        }
      };

      existingNode1 = updateNode(context1, existingNode.id, updateDefinition, null, null, null, tags);

      sleep(0.25);

      // load fresh to deal with any on-commit changes not being reflected in update response (rendered before commit)
      existingNode1 = loadNode(context1, existingNode.id, null, null, null, true, null, tags);
      existingNode2 = loadNode(context2, existingNode.id, null, null, null, true, null, tags);

      inconsistent = existingNode1.properties['cm:hits'] !== existingNode2.properties['cm:hits']
        || existingNode1.properties['cm:from'] !== existingNode2.properties['cm:from']
        || existingNode1.properties['cm:to'] !== existingNode2.properties['cm:to'];
      clusterAfterUpdateValidationError.add(inconsistent);
      if (inconsistent)
      {
         console.log('Inconsistency found after update between ' + JSON.stringify(existingNode1) + ' and ' + JSON.stringify(existingNode2));
      }
    }
  });
}

function updateRandomContentAndVerifyInCluster(rootFolderId, tags)
{
  group('Lookup and modify existing file with verification in cluster', function(){
    let userIds = getUserIds();
    let hostIds = getHostIds();
    let context = {
      userId: userIds[Math.floor(Math.random() * userIds.length)]
    };

    let hostIdx1 = Math.floor(Math.random() * hostIds.length);
    let hostIdx2 = Math.floor(Math.random() * hostIds.length);
    while (hostIds.length > 1 && hostIdx1 === hostIdx2)
    {
        hostIdx2 = Math.floor(Math.random() * hostIds.length);
    }

    let context1 = {
      userId: context.userId,
      host: hostIds[hostIdx1]
    };
    let context2 = {
      userId: context.userId,
      host: hostIds[hostIdx2]
    };

    let existingNode = lookupRandomContentInFolderStructure(context, rootFolderId, 3, null, tags);
    if (existingNode)
    {
      let existingNode1 = loadNode(context1, existingNode.id, null, null, null, true, null, tags);
      let existingNode2 = loadNode(context2, existingNode.id, null, null, null, true, null, tags);

      let inconsistent = existingNode1.properties['cm:hits'] !== existingNode2.properties['cm:hits']
        || existingNode1.properties['cm:from'] !== existingNode2.properties['cm:from']
        || existingNode1.properties['cm:to'] !== existingNode2.properties['cm:to'];
      clusterBeforeUpdateValidationError.add(inconsistent);
      if (inconsistent)
      {
         console.log('Inconsistency found before update between ' + JSON.stringify(existingNode1) + ' and ' + JSON.stringify(existingNode2));
      }

      // users take some time between retrieval + modification
      sleep(0.5 + Math.floor(Math.random() * 1.5));
      
      let effectivityFrom = new Date(y2018StartMillis + Math.floor(Math.random() * (Date.now() - y2018StartMillis)));
      let effectivityTo = new Date(effectivityFrom.getTime() + Math.floor(Math.random() * (Date.now() - effectivityFrom.getTime())));
  
      let updateDefinition = {
        name: createSimpleUuid(),
        properties: {
          'cm:hits': Math.floor(Math.random() * 100),
          'cm:from': effectivityFrom.toISOString(),
          'cm:to': effectivityTo.toISOString()
        }
      };

      existingNode1 = updateNode(context1, existingNode.id, updateDefinition, null, null, null, tags);

      sleep(0.25);

      // load fresh to deal with any on-commit changes not being reflected in update response (rendered before commit)
      existingNode1 = loadNode(context1, existingNode.id, null, null, null, true, null, tags);
      existingNode2 = loadNode(context2, existingNode.id, null, null, null, true, null, tags);

      inconsistent = existingNode1.properties['cm:hits'] !== existingNode2.properties['cm:hits']
        || existingNode1.properties['cm:from'] !== existingNode2.properties['cm:from']
        || existingNode1.properties['cm:to'] !== existingNode2.properties['cm:to'];
      clusterAfterUpdateValidationError.add(inconsistent);
      if (inconsistent)
      {
         console.log('Inconsistency found after update between ' + JSON.stringify(existingNode1) + ' and ' + JSON.stringify(existingNode2));
      }
    }
  });
}

const contentCreationRuns = new Rate('Runs simulating content creation');
const contentPropUpdateRuns = new Rate('Runs simulating property update on content');
const contentPropUpdateVerificationRuns = new Rate('Runs simulating property update on content (with cluster verification)');
const contentUpdateRuns = new Rate('Runs simulating content update on content');

export default function() {
  let rnd = Math.random();
  let isContentCreation = __ITER <= 1 || (rnd >= 0 && rnd < 0.3);
  let isContentPropUpdate = !isContentCreation && rnd >= 0.3 && rnd < 0.8;
  let isContentPropUpdateAndClusterVerification = !isContentCreation && rnd >= 0.8;
  let isContentUpdate = false;

  contentCreationRuns.add(isContentCreation);
  contentPropUpdateRuns.add(isContentPropUpdate);
  contentPropUpdateVerificationRuns.add(isContentPropUpdateAndClusterVerification);
  contentUpdateRuns.add(isContentUpdate);

  if (isContentCreation)
  {
    createNewDummyContent();
  }
  else if (isContentPropUpdate)
  {
    updateRandomContent('-shared-');
  }
  else if (isContentPropUpdateAndClusterVerification)
  {
    updateRandomContentAndVerifyInCluster('-shared-');
  }
};
