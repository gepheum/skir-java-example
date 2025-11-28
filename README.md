# Soia Java example

Example showing how to use soia's [Java code generator](https://github.com/gepheum/soia-java-gen) in a project.

## Build and run the example

```shell
# Download this repository
git clone https://github.com/gepheum/soia-java-example.git

cd soia-java-example

# Install all dependencies, which include the soia compiler and the soia
# Java code generator
npm i

npm run run:snippets
# Same as:
#   npm run build  # .soia to .java codegen
#   ./gradlew run
```

### Start a soia service

From one process, run:
```shell
npm run run:start-service
#  Same as:
#    npm run build
#    ./gradlew run -PmainClass=examples.StartService
```

From another process, run:
```shell
npm run run:call-service
#  Same as:
#    npm run build
#    ./gradlew run -PmainClass=examples.CallService
```
