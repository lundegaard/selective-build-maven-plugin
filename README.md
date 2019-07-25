# Selective Build Maven Plugin

Selective Build Maven Plugin allows you to build and process only modules with changes in it. It is dependent on git client installed on your system.

## Goal `changes` 

This goal is used for selective builds (build only modules with changes). 

It is used in three steps:

1. Configure the plugin in the root POM
2. Run the plugin and put the result into a environment variable
3. Run standard maven build with options from the variable

### Root POM setup

To use this plugin first you should set it up in the root POM like this.

```xml
<project>
    ...
    <build>
        ...
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>eu.lundegaard.maven</groupId>
                    <artifactId>selective-build-maven-plugin</artifactId>
                    <version>0.5.0</version>
                </plugin>
            </plugins>
        </pluginManagement>
        ...
    </build>
    ...
</project>
```

### Get params for the build

The `changes` plugin outputs on _stdout_ parameters for the onward build. These parameters are based on current git status and can result into one of the following states:

1. Non-recursive build (build only root project) -- This happens mainly when there are no changes detected
2. Selective build (build only changed modules)
3. Full rebuild -- this happens when there are too much commits or when special files are changed

To obtain params one usually runs something like this:

```bash
MVN_RUN_OPTS=$(mvn -q -N selective-build:changes -Dsource=1234567890ABCDEF -Dtarget=FEDCBA0987654321)
```

The `source` and `target` properties are either commits or branches between which the changes are detected.

### Run selective Maven build

To run the selective build afterwards is simple. Just run something like this:

```bash
mvn $MVN_RUN_OPTS clean verify 
```
