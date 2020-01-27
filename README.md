[![Build Status](https://travis-ci.org/navikt/dagpenger-joark-mottak.svg?branch=master)](https://travis-ci.org/navikt/dagpenger-joark-mottak)

# Dagpenger-joark-mottak

Tar i mot tynne kafka meldinger fra Joark og produserer tykke kafka meldinger for videre behandling.

# Komme i gang

Gradle brukes som byggverktøy og er bundlet inn. 

`./gradlew build`

# Kjøre lokalt med mocking

Environment variables:  
OIDC_STS_ISSUERURL=http://localhost:8080/castlemock/mock/rest/project/o9jGYV/application/3xK9Jr/stsoidc  
JOURNALFOERINNGAAENDE_V1_URL=http://localhost:8080/castlemock/mock/rest/project/86U51B/application/IeLTvj/  
SRVDAGPENGER_JOARK_MOTTAK_USERNAME=igroup  
SRVDAGPENGER_JOARK_MOTTAK_PASSWORD=itest  

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan rettes mot:

* André Roaldseth, andre.roaldseth@nav.no 
* Eller en annen måte for omverden å kontakte teamet på

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #team-dagpenger.
