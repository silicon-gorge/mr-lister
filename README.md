# Nokia Entertainment Onix

<img src="shared/onix.jpg" height="100" width="100"/>

## Introduction

Onix is a RESTful web service that maintains a list of applications that
are deployed in the Nokia Entertainment cloud computing environment.
Associated with each application is a list of metadata items, each of
which has a key (string) and a value which can be arbitrary json. Methods
are supplied to create new applications and to create, update and delete
metadata items.

The service runs in the cloud and is backed by the Amazon's DynamoDB data
storage service.

## Resources

GET /1.x/ping (returns 'pong')

GET /1.x/status (returns status of the service)

GET /healthcheck (returns '200' or '500' depending whether the service is healthy)

GET /1.x/applications (list applications)

POST /1.x/applications (create a new application)

GET /1.x/applications/{app} (show details of an application)

PUT /1.x/applications/{app}/{key} (create/update a metadata item)

GET /1.x/applications/{app}/{key} (gets a metadata item)

DELETE /1.x/applications/{app}/{key} (deletes a metadata item)

## Notes

There is currently no mechanism for deleting an application.

Slashes at the end of resource paths are optional.

## List Applications

### Resource Details

GET /1.x/applications

Returns the list of applications that exist.

### Example Request

    GET http://onix.ent.nokia.com:8080/1.x/applications/

### Example Response

    200 OK
    Content-Type: application/json; charset=utf-8
    {
      "applications" : [
         "puce",
         "vermilion",
         "rose",
         "amber"
      ]
    }

### Response Codes

200 OK

500 InternalServerError

## Create Applications

### Resource Details

POST /1.x/applications (application/json)

Create a new application.

### Example Request

    POST http://onix.ent.nokia.com:8080/1.x/applications/
    Content-Type: application/json
    {
      "name" : "my-new-app"
    }

### Example Response

    201 Created
    Content-Type: application/json; charset=utf-8
    {
      "name" : "my-new-app"
    }

### Response Codes

201 Created

400 BadRequest

409 Conflict (application already exists)

500 InternalServerError

## Show Details of an Applications

### Resource Details

GET /1.x/applications/{app}

Gets the name and metadata associated with an application.

### Example Request

    GET http://onix.ent.nokia.com:8080/1.x/applications/empoleon

### Example Response

    200 OK
    Content-Type: application/json; charset=utf-8
    {
       "name" : "empoleon",
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

### Response Codes

200 OK

404 NotFound

500 InternalServerError

## Create/Update a Metadata Item

### Resource Details

PUT /1.x/applications/{app}/{key} (application/json)

Creates or updates the metadata of the application {app} with an item called {key} with
the value of the item provided in the body of the request.

### Example Request

    PUT http://onix.ent.nokia.com:8080/1.x/applications/empoleon/stats
    {
      "value" : [
        {
          "hp" : 84
        },
        {
          "attack" : 86
        },
        {
          "defense" : 88
        }
      ]
    }

### Example Response

    201 Created
    Content-Type: application/json; charset=utf-8
    {
      "stats" : [
        {
          "hp" : 84
        },
        {
          "attack" : 86
        },
        {
          "defense" : 88
        }
      ]
    }

### Response Codes

201 Created

400 BadRequest

404 NotFound (application not found)

500 InternalServerError

## Get Application Metadata Item

### Resource Details

GET /1.x/applications/{app}/{key}

Gets a particular piece of metadata identified by the key {key} for the application {app}.

### Example Request

    GET http://onix.ent.nokia.com:8080/1.x/applications/charizard/species

### Example Response

    200 OK
    Content-Type: application/json; charset=utf-8
    {
      "species" : "flame"
    }

### Response Codes

200 OK

404 NotFound (application or metadata item not found)

500 InternalServerError

## Delete an Application Metadata Item

### Resource Details

DELETE /1.x/applications/{app}/{key}

Deletes a particular piece of metadata identified by the key {key} for the application {app}.
This is idempotent so that repeated deletes or deletes on applications and keys that don't
exist will always return the same successful response.

### Example Request

    DELETE http://onix.ent.nokia.com:8080/1.x/applications/venusaur/abilities

### Example Response

    204 NoContent

### Response Codes

204 NoContent

500 InternalServerError

## Healthcheck

### Resource Details

GET /healthcheck

Checks if the service is running and communicating with DynamoDB in AWS.

### Example Request

    GET http://onix.ent.nokia.com:8080/healthcheck/

### Example Response 1

    200 OK
    Content-Type: text/plain;charset=UTF-8
    I am healthy. Thank you for asking.

### Example Response 2

    500 InternalServerError
    Content-Type: text/plain;charset=UTF-8
    I am unwell. Check my logs.

### Response Codes

200 OK

500 InternalServerError
