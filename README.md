# Pet store service

A simple pet store service implemented as a Datomic Ion. Consumes and
emits JSON and supports CRUD operation on Pet resources.

Demonstrates:

- Ionizing a Pedestal service.
- Using Ion Parameters.
- Transacting schema.

## Prerequisites

- You have an AWS account with sufficient privileges which you can use to provision Datomic
  Cloud. If you don't have an AWS account, refer to the AWS
  [documentation](https://aws.amazon.com/premiumsupport/knowledge-center/create-and-activate-aws-account/)
  for instructions on how to set one up. **Note**: Running this sample
  will incur AWS charges. The cost of running Datomic Cloud's Solo
  topology is approximately $1/day.
- You have AWS credentials provisioned locally. Refer to the AWS
[documentation](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/setup-credentials.html)
for details on how to set up credentials.

## Getting Started

### Provisioning and configuring Datomic Cloud

Follow the Datomic Cloud [Setting Up](https://docs.datomic.com/cloud/setting-up.html)
guide to provision your Datomic Cloud environment. Be sure to choose
the _Solo_ Fulfillment option. This sample assumes the __Stack Name_
is `ion-pet-service`.

Once your Datomic Cloud environment is provisioned, continue on to the [Configuring
Access](https://docs.datomic.com/cloud/getting-started/configuring-access.html)
documentation and, finally, the [Getting
Started](https://docs.datomic.com/cloud/getting-started/connecting.html) guide.

### Local Development

You will need to export the following environment variables in order
to run this sample locally:

- `AWS_PROFILE`: The AWS profile in your AWS [credentials file](https://docs.aws.amazon.com/sdk-for-php/v3/developer-guide/guide_credentials_profiles.html).
- `AWS_REGION`: The AWS region in which you have provisioned Datomic
  Cloud.
- `DATOMIC_ENV_MAP`:      See below.
- `DATOMIC_APP_INFO_MAP`: See below.

The Datomic Ion param environment variables should be set as follows:

```
export DATOMIC_ENV_MAP="{:env :dev}""
export DATOMIC_APP_INFO_MAP="{:app-name \"ion-pet-service\" :deployment-group \"$DeploymentGroup\"}"
```

Where `$DeploymentGroup` is the name of your Ion deployment group (see
[Ion Deploy](https://docs.datomic.com/cloud/ions/ions-reference.html#deploy)).

### Parameters

Pedestal Ions provides access to Datomic Ion parameters. Refer to the
[README](https://github.com/cognitect-labs/pedestal.ions/blob/master/README.md#parameters)
for details.

The values of the `:io.pedestal.ions/app-info` and
`:io.pedestal.ions/env-map` keys are pulled from their
respective Datomic Ion param environment variables which were
specified in the `Local Development` section of this guide.

To run this sample, you will need to create a `db-name` parameter in AWS Systems
Manager Parameter Store. This can be done as follows:

`aws ssm put-parameter --name /datomic-shared/dev/ion-pet-service/db-name --value pet-store --type String`

### Deployment

To push the project to your Datomic Cloud environment, execute the
following command from the root directory of the sample project:

`clojure -A:dev -m datomic.ion.dev '{:op :push :uname "pet-service-sample"}'`

We provide a `:uname` key because the sample has a `:local/root` dependency.

This command will return a map containing the key
`:deploy-command`. Copy the value and execute it at the command line
to deploy the ionized app. You will need to unescape the `:uname` value.

The deployment command returns a map as well. This map contains the
key `:status-command`. Copy the value of this key and execute it on
the command line to track the status of the deployment. After a few
seconds you should see a successful result that looks like this:

``` clojure
{:deploy-status "SUCCEEDED", :code-deploy-status "SUCCEEDED"}
```

### Provisioning API Gateway

- Go to the [AWS API Gateway
  console](https://console.aws.amazon.com/apigateway/home) to create a
  new AWS API Gateway for this sample.
- Choose _New API_ from the _Create new API_ options.
- Name your API _pet-service_.
- Click _Create API_.
- Under the _Actions_ dropdown, choose _Create Resource_.
- Check the box _Configure as proxy resource_.
- Click _Create Resource_.

To connect the API to the deployed pet service ion:

- Select the _ANY_ method under `/{proxy+}`.
- Leave the _Integration type_ as _Lambda Function Proxy_.
- Set the _Lambda Function_ to `ion-pet-service-${Group}-app`, where
  `$Group` is your Datomic Cloud compute group.
- Click _Save_.
- Choose _Ok_ to give your API Gateway permission to call your lambda.
- Under your API in the left side of the UI, click on the bottom
  choice _Settings_.
- Choose _Add Binary Media Type_, add the `*/*` type.
- Click _Save Changes_.

To deploy your API:

- Click on your API name and choose _Deploy API_ under the _Actions_
  dropdown.
- Choose _New Stage_ as the _Deployment Stage_, add the stage `dev`,
  then choose _Deploy_.

The top of the Stage Editor will show the url for your app.

Your API is now deployed. Keep in mind that it is public. Be sure to
remove it when done using it!

### Interacting with the deployed app.

The commands that follow expect that you replace `$INVOKE_URL` with
the _Invoke URL_ of your deployed api.

#### Retrieving pets

``` shell
curl -X GET https://$INVOKE_URL/dev/pets
```

You should see the following result:

``` json
[
    {
        "pet-store.pet/id": 2,
        "pet-store.pet/name": "Dante",
        "pet-store.pet/tag": "cat"
    },
    {
        "pet-store.pet/id": 1,
        "pet-store.pet/name": "Yogi",
        "pet-store.pet/tag": "dog"
    }
]
```

#### Add a pet

``` shell
curl -X POST \
  https://$INVOKE_URL/dev/pets \
  -H 'Cache-Control: no-cache' \
  -H 'Content-Type: application/json' \
  -d '    {
        "id": 302,
        "name": "Foobar",
        "tag": "bird"
    }'
```

Which should return a `201: Created`.

Perform another `GET` of `/pets` will include the new pet.

``` shell
curl -X GET https://$INVOKE_URL/dev/pets
```

``` json
[
    {
        "pet-store.pet/id": 2,
        "pet-store.pet/name": "Dante",
        "pet-store.pet/tag": "cat"
    },
    {
        "pet-store.pet/id": 302,
        "pet-store.pet/name": "Foobar",
        "pet-store.pet/tag": "bird"
    },
    {
        "pet-store.pet/id": 1,
        "pet-store.pet/name": "Yogi",
        "pet-store.pet/tag": "dog"
    }
]
```

#### Put a Pet

Update the pet`Foobar`:

``` shell
curl -X PUT \
  https://$INVOKE_URL/dev/pet/302 \
  -H 'Cache-Control: no-cache' \
  -H 'Content-Type: application/json' \
  -d '    {
        "name": "Foox",
        "tag": "bird"
    }'
```

The updated pet will be returned:

``` json
{
    "pet-store.pet/id": 302,
    "pet-store.pet/name": "Foox",
    "pet-store.pet/tag": "Bird"
}
```

#### Delete a pet

Now remove the pet `Foox`:

``` shell
curl -X DELETE \
  https://$INVOKE_URL/dev/pet/302 \
  -H 'Cache-Control: no-cache'
```

A response with the HTTP status `204: No content` will be returned.

### Connecting to Datomic Cloud from a local repl

Start the SOCKS proxy as per the Datomic Cloud [docs](https://docs.datomic.com/cloud/getting-started/connecting.html#socks-proxy).

Start a repl and connect to it:

    $  clj -Adev:log

Instantiate a handler so you can interact with the app:

    user=> (require '[ion-provider.ion :as ion]
                    '[ion-provider.service :as service])

    user=> (def h (ion/handler service/service))

Make some requests:

    user=> (h {:server-port    0
               :server-name    "localhost"
               :remote-addr    "127.0.0.1"
               :uri            "/"
               :scheme         "http"
               :request-method :get
               :headers        {}})

    INFO  io.pedestal.http  - {:msg "GET /", :line 80}
    {:status 200, :headers {"Strict-Transport-Security" "max-age=31536000; includeSubdomains", "X-Frame-Options" "DENY", "X-Content-Type-Options" "nosniff", "X-XSS-Protection" "1; mode=block", "X-Download-Options" "noopen", "X-Permitted-Cross-Domain-Policies" "none", "Content-Security-Policy" "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;", "Content-Type" "text/plain"}, :body "Hello World!"}

    user=> (slurp (:body (h {:server-port    0
                   :server-name    "localhost"
                   :remote-addr    "127.0.0.1"
                   :uri            "/pets"
                   :scheme         "http"
                   :request-method :get})))

    INFO  io.pedestal.http  - {:msg "GET /pets", :line 80}
    ...
    "[{\"pet-store.pet/id\":2,\"pet-store.pet/name\":\"Dante\",\"pet-store.pet/tag\":\"cat\"},{\"pet-store.pet/id\":1,\"pet-store.pet/name\":\"Yogi\",\"pet-store.pet/tag\":\"dog\"}]"

## Cleanup

Follow the Datomic Cloud [Deleting a System](https://docs.datomic.com/cloud/operation/deleting.html)
guide for instructions on removing the provisioned resources.

Delete the policy `Datomic-ion-pet-service-{REGION}`

Delete the CodeDeploy project `ion-pet-service`.
