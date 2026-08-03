#!/bin/bash

# Parse command line parameters
usage() {
  echo "Usage: $0 --camel-quarkus-dir <path> [options]"
  echo ""
  echo "Required parameters:"
  echo "  --camel-quarkus-dir                 Path to the checked-out Camel Quarkus upstream branch"
  echo ""
  echo "Optional parameters:"
  echo "  --mrrc-local                        Path to the local Maven repository (MRRC extract location)"
  echo "                                        (required for product builds, optional for local testing)"
  echo "  --quarkus-platform-version          Platform version to test against"
  echo "                                        (derived from \${MRRC_LOCAL}/.../quarkus-bom/ if not set)"
  echo "  --quarkus-platform-group-id         Platform group ID"
  echo "                                        (default: com.redhat.quarkus.platform)"
  echo "  --quarkus-platform-artifact-id      Platform artifact ID"
  echo "                                        (default: quarkus-bom)"
  echo "  --camel-quarkus-platform-artifact-id  Camel Quarkus platform artifact ID"
  echo "                                        (default: quarkus-camel-bom)"
  echo "  --camel-quarkus-platform-group-id     Camel Quarkus platform group ID"
  echo "                                        (default: value of --quarkus-platform-group-id)"
  echo "  --camel-quarkus-platform-version      Camel Quarkus platform version"
  echo "                                        (default: value of --quarkus-platform-version)"
  echo "  --quarkus-version                   Quarkus version"
  echo "                                        (derived from \${MRRC_LOCAL}/.../quarkus-core/ if not set)"
  echo "  --camel-version                     Camel version"
  echo "                                        (derived from \${MRRC_LOCAL}/.../camel-core/ if not set)"
  echo "  --camel-quarkus-version             Camel Quarkus version (for upstream builds)"
  echo "  --settings-xml                      Path to Maven settings.xml (uses Maven default if not set)"
  echo "  --mvn-extra                         Extra arguments to pass to every mvn invocation"
  echo "                                        (e.g., test timeouts, custom properties)"
  echo "  --mvn-test-extra-args               Extra arguments to pass only to integration test mvn invocation"
  echo "                                        (e.g., -am -amd -pl modules)"
  echo "  --native                            Run native tests"
  echo "                                        Adds: -Pnative,docker -Dtest=!* -Dsurefire.failIfNoSpecifiedTests=false"
  echo "  --test-modules                      Comma-separated list of modules to test (adds -pl <modules>)"
  echo "  --test-classes                      Comma-separated list of test classes to run"
  echo "  --test-debug                        Enable Maven debug mode for tests"
  echo "                                        (JVM: -Dmaven.surefire.debug, Native: -Dmaven.failsafe.debug)"
  echo "  --prepare-only                      Only run preparation (BOM/POM, test-support), skip tests"
  echo "  --test-only                         Skip preparation, only run tests (useful for local development)"
  echo "  --check-errors                      Check Maven output for error strings and fail if found"
  echo "                                        (similar to omega.maven.invoke checkErrors behavior)"
  echo "  --run-against-mrrc-only             Run against MRRC only (enables special handling for product builds)"
  echo "  --upstream                          Running against upstream (community) build"
  echo "  --verbose                           Enable verbose output"
  echo "  --debug                             Enable command tracing (set -x)"
  exit 1
}

while [[ $# -gt 0 ]]; do
  case $1 in
    --camel-quarkus-dir)
      CAMEL_QUARKUS_DIR="$2"
      shift 2
      ;;
    --mrrc-local)
      MRRC_LOCAL="$2"
      shift 2
      ;;
    --quarkus-platform-version)
      QUARKUS_PLATFORM_VERSION="$2"
      shift 2
      ;;
    --quarkus-platform-group-id)
      QUARKUS_PLATFORM_GROUP_ID="$2"
      shift 2
      ;;
    --quarkus-platform-artifact-id)
      QUARKUS_PLATFORM_ARTIFACT_ID="$2"
      shift 2
      ;;
    --quarkus-version)
      QUARKUS_VERSION="$2"
      shift 2
      ;;
    --camel-version)
      CAMEL_VERSION="$2"
      shift 2
      ;;
    --camel-quarkus-version)
      CAMEL_QUARKUS_VERSION="$2"
      shift 2
      ;;
    --settings-xml)
      SETTINGS_XML="$2"
      shift 2
      ;;
    --camel-quarkus-platform-artifact-id)
      CAMEL_QUARKUS_PLATFORM_ARTIFACT_ID="$2"
      shift 2
      ;;
    --camel-quarkus-platform-group-id)
      CAMEL_QUARKUS_PLATFORM_GROUP_ID="$2"
      shift 2
      ;;
    --camel-quarkus-platform-version)
      CAMEL_QUARKUS_PLATFORM_VERSION="$2"
      shift 2
      ;;
    --mvn-extra)
      MVN_EXTRA="$2"
      shift 2
      ;;
    --mvn-test-extra-args)
      MVN_TEST_EXTRA_ARGS="$2"
      shift 2
      ;;
    --native)
      NATIVE=true
      shift
      ;;
    --test-modules)
      TEST_MODULES="$2"
      shift 2
      ;;
    --test-classes)
      TEST_CLASSES="$2"
      shift 2
      ;;
    --test-debug)
      TEST_DEBUG=true
      shift
      ;;
    --test-only)
      TEST_ONLY=true
      shift
      ;;
    --prepare-only)
      PREPARE_ONLY=true
      shift
      ;;
    --check-errors)
      CHECK_ERRORS=true
      shift
      ;;
    --run-against-mrrc-only)
      RUN_AGAINST_MRRC_ONLY=true
      shift
      ;;
    --upstream)
      UPSTREAM=true
      shift
      ;;
    --verbose)
      VERBOSE=true
      shift
      ;;
    --debug)
      set -x
      shift
      ;;
    -h|--help)
      usage
      ;;
    *)
      echo "Unknown parameter: $1"
      usage
      ;;
  esac
done

# Validate required parameters
if [[ -z "$CAMEL_QUARKUS_DIR" ]]; then
  echo "Error: --camel-quarkus-dir is required."
  usage
fi

QUARKUS_PLATFORM_GROUP_ID="${QUARKUS_PLATFORM_GROUP_ID:-com.redhat.quarkus.platform}"
QUARKUS_PLATFORM_ARTIFACT_ID="${QUARKUS_PLATFORM_ARTIFACT_ID:-quarkus-bom}"
CAMEL_QUARKUS_PLATFORM_ARTIFACT_ID="${CAMEL_QUARKUS_PLATFORM_ARTIFACT_ID:-quarkus-camel-bom}"

# Only add -s flag if settings.xml is specified
if [[ -n "$SETTINGS_XML" ]]; then
  SETTINGS_XML_ARG="-s $SETTINGS_XML"
else
  SETTINGS_XML_ARG=""
fi

# Only add -Dmaven.repo.local if mrrc-local is specified
if [[ -n "$MRRC_LOCAL" ]]; then
  MAVEN_REPO_LOCAL_ARG="-Dmaven.repo.local=$MRRC_LOCAL"
else
  MAVEN_REPO_LOCAL_ARG=""
fi

# Determine native/JVM specific settings
NATIVE_PROFILE="-Pnative,docker"
NATIVE_TEST_ARGS="-Dtest=!* -Dsurefire.failIfNoSpecifiedTests=false"

if [[ "$NATIVE" == "true" ]]; then
  INTEGRATION_TEST_PHASE="verify"
  NATIVE_MVN_ARGS="$NATIVE_PROFILE $NATIVE_TEST_ARGS"
else
  INTEGRATION_TEST_PHASE="install"
  NATIVE_MVN_ARGS=""
fi

if [[ -n "$TEST_MODULES" ]]; then
  TEST_MODULES_ARG="-pl $TEST_MODULES"
else
  TEST_MODULES_ARG=""
fi

if [[ -n "$TEST_CLASSES" ]]; then
  if [[ "$NATIVE" == "true" ]]; then
    TEST_CLASSES_ARG="-Dit.test=$TEST_CLASSES"
  else
    TEST_CLASSES_ARG="-Dtest=$TEST_CLASSES"
  fi
else
  TEST_CLASSES_ARG=""
fi

if [[ "$TEST_DEBUG" == "true" ]]; then
  if [[ "$NATIVE" == "true" ]]; then
    TEST_DEBUG_ARG="-Dmaven.failsafe.debug"
  else
    TEST_DEBUG_ARG="-Dmaven.surefire.debug"
  fi
else
  TEST_DEBUG_ARG=""
fi

log() {
  if [[ "$VERBOSE" == "true" ]]; then
    echo "$@"
  fi
}

cd "$CAMEL_QUARKUS_DIR" || { echo "Error: cannot cd to $CAMEL_QUARKUS_DIR"; exit 1; }

log "CAMEL_QUARKUS_DIR = $CAMEL_QUARKUS_DIR"
log "MRRC_LOCAL = $MRRC_LOCAL"
log "SETTINGS_XML = $SETTINGS_XML"
log "MVN_EXTRA = $MVN_EXTRA"
log "PREPARE_ONLY = $PREPARE_ONLY"
log "RUN_AGAINST_MRRC_ONLY = $RUN_AGAINST_MRRC_ONLY"
log "UPSTREAM = $UPSTREAM"

if [[ -z "$QUARKUS_PLATFORM_VERSION" && -n "$MRRC_LOCAL" ]]; then
  QUARKUS_PLATFORM_VERSION="$(ls "${MRRC_LOCAL}/com/redhat/quarkus/platform/quarkus-bom/" 2>/dev/null | head -1)"
  log "QUARKUS_PLATFORM_VERSION derived from MRRC_LOCAL"
fi
log "QUARKUS_PLATFORM_GROUP_ID = $QUARKUS_PLATFORM_GROUP_ID"
log "QUARKUS_PLATFORM_ARTIFACT_ID = $QUARKUS_PLATFORM_ARTIFACT_ID"
log "QUARKUS_PLATFORM_VERSION = $QUARKUS_PLATFORM_VERSION"

CAMEL_QUARKUS_PLATFORM_GROUP_ID="${CAMEL_QUARKUS_PLATFORM_GROUP_ID:-$QUARKUS_PLATFORM_GROUP_ID}"
log "CAMEL_QUARKUS_PLATFORM_GROUP_ID = $CAMEL_QUARKUS_PLATFORM_GROUP_ID"

log "CAMEL_QUARKUS_PLATFORM_ARTIFACT_ID = $CAMEL_QUARKUS_PLATFORM_ARTIFACT_ID"

CAMEL_QUARKUS_PLATFORM_VERSION="${CAMEL_QUARKUS_PLATFORM_VERSION:-$QUARKUS_PLATFORM_VERSION}"
log "CAMEL_QUARKUS_PLATFORM_VERSION = $CAMEL_QUARKUS_PLATFORM_VERSION"

if [[ -z "$QUARKUS_VERSION" && -n "$MRRC_LOCAL" && "$RUN_AGAINST_MRRC_ONLY" == "true" ]]; then
  QUARKUS_VERSION="$(ls "${MRRC_LOCAL}/io/quarkus/quarkus-core/" 2>/dev/null | head -1)"
  log "QUARKUS_VERSION derived from MRRC_LOCAL"
fi
log "QUARKUS_VERSION = $QUARKUS_VERSION"

# Validate that QUARKUS_VERSION is set when RUN_AGAINST_MRRC_ONLY is enabled
if [[ "$RUN_AGAINST_MRRC_ONLY" == "true" && -z "$QUARKUS_VERSION" ]]; then
  echo "Error: When --run-against-mrrc-only is set, QUARKUS_VERSION must be specified either via:"
  echo "  1. --quarkus-version parameter, or"
  echo "  2. --mrrc-local (QUARKUS_VERSION will be derived from MRRC)"
  exit 1
fi

# Validate that CAMEL_QUARKUS_VERSION is set when RUN_AGAINST_MRRC_ONLY is NOT enabled
if [[ "$RUN_AGAINST_MRRC_ONLY" != "true" && "$UPSTREAM" != "true" && -z "$CAMEL_QUARKUS_VERSION" ]]; then
  echo "Error: When --run-against-mrrc-only is NOT set, CAMEL_QUARKUS_VERSION must be specified via:"
  echo "  --camel-quarkus-version parameter"
  exit 1
fi

if [[ -z "$CAMEL_VERSION" && -n "$MRRC_LOCAL" && "$RUN_AGAINST_MRRC_ONLY" == "true" ]]; then
  CAMEL_VERSION="$(ls "${MRRC_LOCAL}/org/apache/camel/camel-direct/" 2>/dev/null | head -1)"
  log "CAMEL_VERSION derived from MRRC_LOCAL"
fi
log "CAMEL_VERSION = $CAMEL_VERSION"

# Validate that CAMEL_VERSION is set when RUN_AGAINST_MRRC_ONLY is enabled
if [[ "$RUN_AGAINST_MRRC_ONLY" == "true" && -z "$CAMEL_VERSION" ]]; then
  echo "Error: When --run-against-mrrc-only is set, CAMEL_VERSION must be specified either via:"
  echo "  1. --camel-version parameter, or"
  echo "  2. --mrrc-local (CAMEL_VERSION will be derived from MRRC)"
  exit 1
fi

# Build common Maven arguments
COMMON_MVN_ARGS="-B -ntp -Dformatter.skip -Dimpsort.skip -Denforcer.skip -DbuildMetaData.skip -Dcq.camel-prod-excludes.skip=true -Dcamel-quarkus.update-extension-doc-page.skip -Djava.home=$JAVA_HOME"

if [[ "$UPSTREAM" != "true" ]]; then
  COMMON_MVN_ARGS+=" -Dcq.prod-artifacts.skip=true"
  COMMON_MVN_ARGS+=" -DnoVirtualDependencies"
  COMMON_MVN_ARGS+=" -Dquarkus.platform.group-id=$QUARKUS_PLATFORM_GROUP_ID"
  COMMON_MVN_ARGS+=" -Dquarkus.platform.artifact-id=$QUARKUS_PLATFORM_ARTIFACT_ID"
  COMMON_MVN_ARGS+=" -Dquarkus.platform.version=$QUARKUS_PLATFORM_VERSION"
  COMMON_MVN_ARGS+=" -Dcamel-quarkus.platform.group-id=$CAMEL_QUARKUS_PLATFORM_GROUP_ID"
  COMMON_MVN_ARGS+=" -Dcamel-quarkus.platform.artifact-id=$CAMEL_QUARKUS_PLATFORM_ARTIFACT_ID"
  COMMON_MVN_ARGS+=" -Dcamel-quarkus.platform.version=$CAMEL_QUARKUS_PLATFORM_VERSION"

  if [[ "$RUN_AGAINST_MRRC_ONLY" == "true" ]]; then
    COMMON_MVN_ARGS+=" -Dquarkus.version=$QUARKUS_VERSION"
  fi

  # Repository flags
  # For Jira and CICS components and SAP
  COMMON_MVN_ARGS+=" -Drepo.atlassian-public -Drepo.ibm-cics-internal -Drepo.sap-internal -Drepo.shibboleth"

  if [[ "$RUN_AGAINST_MRRC_ONLY" != "true" ]]; then
    if [[ -n "$CAMEL_QUARKUS_VERSION" ]]; then
      COMMON_MVN_ARGS+=" -Dcamel-quarkus.version=$CAMEL_QUARKUS_VERSION"
    fi
    COMMON_MVN_ARGS+=" -Drepo.rh-indy -Drepo.fuse-qe-nexus"

    # Add temporary repo if platform version contains "temporary"
    if [[ "$QUARKUS_PLATFORM_VERSION" == *"temporary"* ]]; then
      COMMON_MVN_ARGS+=" -Drepo.rh-indy-temporary"
    fi
  fi
fi

# Preparation phase (skip if --test-only)
if [[ "$TEST_ONLY" != "true" ]]; then
  # BOM/POM preparation (only for MRRC builds, not for upstream)
  if [[ "$UPSTREAM" != "true" && "$RUN_AGAINST_MRRC_ONLY" == "true" ]]; then
    log "=== BOM/POM Preparation ==="

    # Hack to fix the missing Jetty BOM (CEQ-8802)
    if [[ -f "poms/bom/src/main/generated/flattened-reduced-pom.xml" ]]; then
      log "Copying flattened-reduced-pom.xml to poms/bom/pom.xml"
      cp poms/bom/src/main/generated/flattened-reduced-pom.xml poms/bom/pom.xml || exit $?
    fi

    # Replace Camel version in the top pom.xml
    if [[ -n "$CAMEL_VERSION" ]]; then
      log "Replacing Camel version to $CAMEL_VERSION in pom.xml"

      # Update parent version using sed (cross-platform: works on Git Bash/Windows, Linux, macOS)
      log "Updating parent version to $CAMEL_VERSION"
      sed '/<parent>/,/<\/parent>/ s|<version>[^<]*</version>|<version>'"$CAMEL_VERSION"'</version>|' pom.xml > pom.xml.tmp && mv pom.xml.tmp pom.xml
      if [[ $? -ne 0 ]]; then
        echo "Error: Failed to replace Camel parent version in pom.xml"
        exit 1
      fi
      # Verify the parent version was actually changed
      if ! grep -q "<parent>" pom.xml || ! grep -A 3 "<parent>" pom.xml | grep -q "<version>$CAMEL_VERSION</version>"; then
        echo "Error: Parent version was not updated to $CAMEL_VERSION in pom.xml"
        exit 1
      fi

      # Update camel.version property using sed
      log "Updating camel.version property to $CAMEL_VERSION"
      sed 's|<camel\.version>[^<]*</camel\.version>|<camel.version>'"$CAMEL_VERSION"'</camel.version>|' pom.xml > pom.xml.tmp && mv pom.xml.tmp pom.xml
      if [[ $? -ne 0 ]]; then
        echo "Error: Failed to replace Camel version property in pom.xml"
        exit 1
      fi
      # Verify the camel.version property was actually changed
      if ! grep -q "<camel\.version>$CAMEL_VERSION</camel\.version>" pom.xml; then
        echo "Error: camel.version property was not updated to $CAMEL_VERSION in pom.xml"
        exit 1
      fi
    fi

    # Replace Quarkus version in the top pom.xml
    if [[ -n "$QUARKUS_VERSION" ]]; then
      log "Replacing Quarkus version to $QUARKUS_VERSION in pom.xml"

      # Update quarkus.version property using sed (cross-platform)
      sed 's|<quarkus\.version>[^<]*</quarkus\.version>|<quarkus.version>'"$QUARKUS_VERSION"'</quarkus.version>|' pom.xml > pom.xml.tmp && mv pom.xml.tmp pom.xml
      if [[ $? -ne 0 ]]; then
        echo "Error: Failed to replace Quarkus version property in pom.xml"
        exit 1
      fi
      # Verify the quarkus.version property was actually changed
      if ! grep -q "<quarkus\.version>$QUARKUS_VERSION</quarkus\.version>" pom.xml; then
        echo "Error: quarkus.version property was not updated to $QUARKUS_VERSION in pom.xml"
        exit 1
      fi
    fi
  fi

  # Install test-support modules (only for MRRC builds, not for upstream)
  if [[ "$UPSTREAM" != "true" && "$RUN_AGAINST_MRRC_ONLY" == "true" ]]; then
    log "=== Installing test-support modules ==="

    TEST_SUPPORT_INSTALL_ARGS="clean install -DskipTests $COMMON_MVN_ARGS -Dcq.camel-prod-excludes.skip=true -Dcamel-quarkus.update-extension-doc-page.skip"

    # Build list of test support modules
    supportModules=("-N" "-f poms/pom.xml" "-f integration-tests-support/pom.xml")

    # Add messaging and http support modules if they exist
    if [[ -f "integration-tests-support/messaging/pom.xml" ]]; then
      supportModules+=("-f integration-tests-support/messaging/pom.xml")
    fi

    if [[ -f "integration-tests-support/http/pom.xml" ]]; then
      supportModules+=("-f integration-tests-support/http/pom.xml")
    fi

    for module in "${supportModules[@]}"; do
      log "$(echo "+ mvn $TEST_SUPPORT_INSTALL_ARGS $MAVEN_REPO_LOCAL_ARG $SETTINGS_XML_ARG $MVN_EXTRA $module" | tr -s ' ')"
      mvn $TEST_SUPPORT_INSTALL_ARGS \
        $MAVEN_REPO_LOCAL_ARG \
        $SETTINGS_XML_ARG \
        $MVN_EXTRA \
        $module
      MVN_EXIT_CODE=$?
      if [[ $MVN_EXIT_CODE -ne 0 ]]; then
        echo "Error: Maven build failed for module $module with exit code $MVN_EXIT_CODE"
        exit $MVN_EXIT_CODE
      fi
    done
  fi
fi

# Exit if prepare-only mode
if [[ "$PREPARE_ONLY" == "true" ]]; then
  log "=== Preparation completed successfully ==="
  exit 0
fi

# Validate that either test-modules or mvn-test-extra-args is specified for test execution
# Note: mvn-test-extra-args can be empty string when not needed
if [[ -z "$TEST_MODULES" && -z "${MVN_TEST_EXTRA_ARGS+x}" ]]; then
  echo "Error: Either --test-modules or --mvn-test-extra-args is required when running tests"
  echo ""
  echo "Examples:"
  echo "  $0 --test-modules 'integration-tests/http'"
  echo "  $0 --test-modules 'integration-tests/http,integration-tests/rest'"
  echo "  $0 --mvn-test-extra-args '-am -amd -pl integration-tests/http'"
  echo "  $0 --mvn-test-extra-args ''  # empty is OK when not needed"
  exit 1
fi

# Build complete Maven command
MVN_CMD="$(echo "-V clean $INTEGRATION_TEST_PHASE -fae $SETTINGS_XML_ARG $COMMON_MVN_ARGS $NATIVE_MVN_ARGS $MVN_EXTRA $TEST_MODULES_ARG $TEST_CLASSES_ARG $TEST_DEBUG_ARG $MVN_TEST_EXTRA_ARGS $MAVEN_REPO_LOCAL_ARG" | tr -s ' ')"

# Run the integration tests
log "=== Running integration tests ==="
log "+ mvn $MVN_CMD"

if [[ "$CHECK_ERRORS" == "true" ]]; then
  # Create temporary file for Maven output
  MVN_OUTPUT_FILE=$(mktemp)
  trap "rm -f $MVN_OUTPUT_FILE" EXIT

  # Run Maven and capture output
  mvn $MVN_CMD 2>&1 | tee "$MVN_OUTPUT_FILE"
  MVN_EXIT_CODE=${PIPESTATUS[0]}

  log "=== Integration tests finished with Maven exit code: $MVN_EXIT_CODE ==="

  # Check for error strings in Maven output (similar to omega.maven.invoke checkErrors)
  ERROR_STRINGS=("Compilation failure" "Could not resolve dependencies" "problems were encountered")

  for ERROR_STR in "${ERROR_STRINGS[@]}"; do
    if grep -q "$ERROR_STR" "$MVN_OUTPUT_FILE"; then
      log "ERROR: Maven output contains string '$ERROR_STR', failing build with exit code 2"
      exit 2  # Exit code 2 = error strings found (Jenkins will mark as FAILURE - red)
    fi
  done

  # No error strings found, but Maven might have failed with test failures
  if [[ $MVN_EXIT_CODE -ne 0 ]]; then
    log "Maven tests failed but no error strings found, exiting with code 1"
    exit 1  # Exit code 1 = test failures only (Jenkins will mark as UNSTABLE - orange)
  fi

  log "All tests passed successfully"
  exit 0  # Exit code 0 = all OK
else
  # Run Maven normally without error checking
  mvn $MVN_CMD
  EXIT_CODE=$?
  log "=== Integration tests finished with exit code: $EXIT_CODE ==="
  exit $EXIT_CODE
fi
