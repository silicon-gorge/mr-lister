# Mr. Lister

## Introduction

Lister is a RESTful web service which maintains a list of applications which are deployed that maintains a list of applications that are deployed using the MixRadio cloud-tooling and the environments they are deployed in to. The RESTful interface allows the creating, updating and deleting of the metadata.

Each application and environment can be thought of as a JSON object with a `name` and a `metadata` property. The `metadata` is an object containing arbitrary key/value pairs where the value is assumed to be JSON.

For a fictional `search` application the output from Lister might look like:

```json
{
  "name": "search",
  "metadata": {
    "contact": "me@email.com"
  }
}
```

The application uses [Amazon's DynamoDB](http://aws.amazon.com/dynamodb/) for its storage.

## Resources

* `GET /ping` - returns 'pong'
* `GET /healthcheck` - returns a JSON response giving some information on whether the application is healthy (the status will be `200` or `500` depending whether the application is healthy or not)
* `GET /applications` - list applications
* `GET /applications/{app}` - show details of an application
* `PUT /applications/{app}` - create a new application
* `DELETE /applications/{app}` - delete an application
* `GET /applications/{app}/{key}` - gets a metadata item for an application
* `PUT /applications/{app}/{key}` - create/update a metadata item against the application
* `DELETE /applications/{app}/{key}` - deletes a metadata item from an application
* `GET /environments` - list environments
* `GET /environments/{env}` - show details of an environment
* `PUT /environments/{env}` - create a new environment
* `DELETE /environments/{env}` - delete an environment

## List applications

### Resource details

`GET /applications`

Returns the list of applications that exist.

### Example request

    GET http://lister/applications/

### Example response

    200 OK
    Content-Type: application/json; charset=utf-8
    {
      "applications" : [
         "amber",
         "puce",
         "rose",
         "vermillion"
      ]
    }

### Response codes

200 OK

500 InternalServerError

## Create application

### Resource details

`PUT /applications/{app}`

Create (technically upserts) a new application (called 'search' in this example).

### Example request

    POST http://lister/applications/search

### Example response

    201 Created
    Content-Type: application/json; charset=utf-8
    {
      "name" : "search"
    }

### Response codes

201 Created

500 InternalServerError

## Show details of an application

### Resource details

`GET /applications/{app}`

Gets the name and metadata associated with an application.

### Example request

    GET http://lister/applications/search

### Example response

    200 OK
    Content-Type: application/json; charset=utf-8
    {
       "name" : "search",
       "metadata" : [
         {
           "key1" : "string value 1"
         },
         {
           "key2" : {
              "some" : "json"
           }
           "key3" : {
             [
               {
                 "some" : "more"
               },
               {
                 "complicated" : "json"
               }
             ]
           }
         }
       ]
     }

### Response codes

200 OK

404 NotFound

500 InternalServerError

## Create/update a metadata item

### Resource details

`PUT /1.x/applications/{app}/{key}` (application/json)

Creates or updates the metadata of the application {app} with an item called {key} with the value of the item provided in the body of the request.

### Example request

    PUT http://lister/1.x/applications/search/property
    {
      "value" : [
        "value 1",
        "value 2",
        "value 3"
      ]
    }

### Example response

    201 Created
    Content-Type: application/json; charset=utf-8
    {
      "value" : [
        "value 1",
        "value 2",
        "value 3"
      ]
    }

### Response codes

201 Created

400 BadRequest

404 NotFound (application not found)

500 InternalServerError

## Get application metadata item

### Resource details

`GET /1.x/applications/{app}/{key}`

Gets a particular item of metadata identified by the key {key} for the application {app}.

### Example request

    GET http://lister/applications/search/property

### Example response

    200 OK
    Content-Type: application/json; charset=utf-8
    {
      "value" : "anything"
    }

### Response codes

200 OK

404 NotFound (application or metadata item not found)

500 InternalServerError

## Delete an application metadata item

### Resource details

`DELETE /1.x/applications/{app}/{key}`

Deletes a particular piece of metadata identified by the key {key} for the application {app}. This is idempotent so that repeated deletes or deletes on applications and keys that don't exist will always return the same successful response.

### Example request

    DELETE http://lister/applications/search/something

### Example response

    204 NoContent

### Response codes

204 NoContent

500 InternalServerError

## Healthcheck

### Resource details

GET /healthcheck

Checks if the service is running and communicating with DynamoDB in AWS.

### Example request

    GET http://lister/healthcheck

### Example response 1

    200 OK
    Content-Type: application/json; charset=utf-8
    {
      "dependencies": [
        {
          "name": "dynamo-applications",
          "success": true
        },
        {
          "name": "dynamo-environments",
          "success": true
        }
      ],
      "name": "lister",
      "success": true,
      "version": "1.0.0"
    }

### Example response 2

    500 InternalServerError
    Content-Type: application/json; charset=utf-8
    {
      "dependencies": [
        {
          "name": "dynamo-applications",
          "success": false
        },
        {
          "name": "dynamo-environments",
          "success": true
        }
      ],
      "name": "lister",
      "success": false,
      "version": "1.0.0"
    }

### Response codes

200 OK

500 InternalServerError
