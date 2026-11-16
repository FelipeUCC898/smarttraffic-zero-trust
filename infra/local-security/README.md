# Local security integration tests

This isolated Docker project exposes only `127.0.0.1:15671`. It does not use AWS
or the normal development brokers. Requires Docker, Maven and Java 17+.

```powershell
./infra/local-security/Test-Security.ps1 -MavenCommand ./.tools/apache-maven-3.9.11/bin/mvn.cmd
```

The script generates disposable RSA/TLS fixtures under ignored `.tools`, starts
RabbitMQ with only its OAuth2 authentication backend, runs opt-in integration
tests, and stops the test broker in `finally`. Certificates last two days. Expired
fixtures must be regenerated; never reuse them for AWS. `server.key` is readable
by the local broker UID for testing only; `jwt-private.pem` is never mounted into
RabbitMQ. The fixture PKCS12 password `test-only` is not an application secret.

The broker authenticates clients using JWTs, not client TLS certificates.
`ssl_options.verify=verify_none` on the **server** means no mTLS requirement;
Java **clients** still validate the server certificate and hostname.

Test coverage: signed RS256 login and resource permissions; denial of expired,
tampered, wrong-audience and guest credentials; hostname and CA validation;
topic bindings and correlated replies between two exclusive client queues;
HTTPS token retrieval with a private CA, and rejection without that CA.

Static public-key pinning is used to isolate broker validation from JWKS network
discovery. This does not test cloud JWKS fetching or rotation. The current key ID
is `smarttraffic-rs256-v1`; rotate it together with configured public signing keys.
No Kafka, PostgreSQL or full application pipeline is included in this harness.

Permissions follow the [RabbitMQ OAuth2 scope format](https://www.rabbitmq.com/docs/oauth2).
The vhost `/smarttraffic` is URL-encoded inside scopes, which are scoped by role
to individual resource names. Query replicas share one service identity and have
permissions within `query_answers.query-client.*`; they are not separate tenants.
