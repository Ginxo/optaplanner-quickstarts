import org.kie.jenkins.jobdsl.templates.KogitoJobTemplate
import org.kie.jenkins.jobdsl.KogitoConstants
import org.kie.jenkins.jobdsl.Utils
import org.kie.jenkins.jobdsl.KogitoJobType

JENKINS_PATH = '.ci/jenkins'
BUILD_CHAIN_JENKINS_PATH = "${JENKINS_PATH}/Jenkinsfile.buildchain"
PR_REPO_URL = 'https://github.com/kiegroup/optaplanner'

def getDefaultJobParams(String repoName = 'optaplanner-quickstarts') {
    return KogitoJobTemplate.getDefaultJobParams(this, repoName)
}

Map getMultijobPRConfig() {
    return [
        parallel: true,
        jobs : [
            [
                id: 'optaplanner',
                primary: true,
                // TODO remove once https://issues.redhat.com/browse/KOGITO-4113 is done 
                // as it will become the default path
                jenkinsfile: BUILD_CHAIN_JENKINS_PATH,
                git: [
                    repo_url: PR_REPO_URL
                ],
            ], [
                id: 'apps',
                repository: 'kogito-apps',
                jenkinsfile: BUILD_CHAIN_JENKINS_PATH,
                git: [
                    repo_url: PR_REPO_URL
                ],
            ], [
                id: 'examples',
                repository: 'kogito-examples',
                jenkinsfile: BUILD_CHAIN_JENKINS_PATH,
                git: [
                    repo_url: PR_REPO_URL
                ],
            ], [
                id: 'runtimes',
                repository: 'kogito-runtimes',
                jenkinsfile: BUILD_CHAIN_JENKINS_PATH,
                git: [
                    repo_url: PR_REPO_URL
                ],
            ], [
                id: 'rostering',
                repository: 'optaweb-employee-rostering',
                jenkinsfile: BUILD_CHAIN_JENKINS_PATH,
                git: [
                    repo_url: PR_REPO_URL
                ],
            ], [
                id: 'routing',
                repository: 'optaweb-vehicle-routing',
                jenkinsfile: BUILD_CHAIN_JENKINS_PATH,
                git: [
                    repo_url: PR_REPO_URL
                ],
            ], [
                id: 'quickstarts',
                repository: 'optaplanner-quickstarts',
                jenkinsfile: BUILD_CHAIN_JENKINS_PATH,
                git: [
                    repo_url: PR_REPO_URL
                ],
            ]
        ],
        extraEnv : [
            // Sonarcloud analysis only on main branch
            // As we have only Community edition
            ENABLE_SONARCLOUD: Utils.isMainBranch(this)
        ]
    ]
}

def getJobParams(String jobName, String jobFolder, String jenkinsfileName, String jobDescription = '') {
    def jobParams = getDefaultJobParams()
    jobParams.job.name = jobName
    jobParams.job.folder = jobFolder
    jobParams.jenkinsfile = jenkinsfileName
    if (jobDescription) {
        jobParams.job.description = jobDescription
    }
    return jobParams
}


// Optaplanner PR checks
setupMultijobPrDefaultChecks()
setupMultijobPrNativeChecks()
setupMultijobPrLTSChecks()


/////////////////////////////////////////////////////////////////
// Methods
/////////////////////////////////////////////////////////////////


void setupMultijobPrDefaultChecks() {
    KogitoJobTemplate.createMultijobPRJobs(this, getMultijobPRConfig()) { return getDefaultJobParams() }
}

void setupMultijobPrNativeChecks() {
    KogitoJobTemplate.createMultijobNativePRJobs(this, getMultijobPRConfig()) { return getDefaultJobParams() }
}

void setupMultijobPrLTSChecks() {
    KogitoJobTemplate.createMultijobLTSPRJobs(this, getMultijobPRConfig()) { return getDefaultJobParams() }
}

void setupDeployJob(String jobFolder, KogitoJobType jobType) {
    def jobParams = getJobParams('optaplanner-deploy', jobFolder, "${JENKINS_PATH}/Jenkinsfile.deploy", 'Optaplanner Deploy')
    if (jobType == KogitoJobType.PR) {
        jobParams.git.branch = '${GIT_BRABUILD_BRANCH_NAMENCH_NAME}'
        jobParams.git.author = '${GIT_AUTHOR}'
        jobParams.git.project_url = Utils.createProjectUrl("${GIT_AUTHOR_NAME}", jobParams.git.repository)
    }
    KogitoJobTemplate.createPipelineJob(this, jobParams).with {
        parameters {
            stringParam('DISPLAY_NAME', '', 'Setup a specific build display name')

            stringParam('BUILD_BRANCH_NAME', "${GIT_BRANCH}", 'Set the Git branch to checkout')
            if (jobType == KogitoJobType.PR) {
                // author can be changed as param only for PR behavior, due to source branch/target, else it is considered as an env
                stringParam('GIT_AUTHOR', "${GIT_AUTHOR_NAME}", 'Set the Git author to checkout')
            }

            booleanParam('SKIP_TESTS', false, 'Skip tests')
            booleanParam('SKIP_INTEGRATION_TESTS',  false, 'Skip long integration tests')

            booleanParam('CREATE_PR', false, 'Should we create a PR with the changes ?')
            stringParam('PROJECT_VERSION', '', 'Optional if not RELEASE. If RELEASE, cannot be empty.')
            stringParam('KOGITO_VERSION', '', 'Optional if not RELEASE. If RELEASE, cannot be empty.')

            if (jobType == KogitoJobType.PR) {
                stringParam('PR_TARGET_BRANCH', '', 'What is the target branch of the PR?')
            }

            //Build branch name for quickstarts
            stringParam('QUICKSTARTS_BUILD_BRANCH_NAME', "${GIT_BRANCH}" == 'master' ? 'development' : "${GIT_BRANCH}", 'Base branch for quickstarts. Set if you are not on a multibranch pipeline.')
        }

        environmentVariables {
            env('PROPERTIES_FILE_NAME', 'deployment.properties')

            env('RELEASE', jobType == KogitoJobType.RELEASE)
            env('JENKINS_EMAIL_CREDS_ID', "${JENKINS_EMAIL_CREDS_ID}")
            env('MAVEN_SETTINGS_CONFIG_FILE_ID', "${MAVEN_SETTINGS_FILE_ID}")

            if (jobType == KogitoJobType.PR) {
                env('MAVEN_DEPENDENCIES_REPOSITORY', "${MAVEN_PR_CHECKS_REPOSITORY_URL}")
                env('MAVEN_DEPLOY_REPOSITORY', "${MAVEN_PR_CHECKS_REPOSITORY_URL}")
                env('MAVEN_REPO_CREDS_ID', "${MAVEN_PR_CHECKS_REPOSITORY_CREDS_ID}")
            } else {
                env('GIT_AUTHOR', "${GIT_AUTHOR_NAME}")

                env('AUTHOR_CREDS_ID', "${GIT_AUTHOR_CREDENTIALS_ID}")
                env('GITHUB_TOKEN_CREDS_ID', "${GIT_AUTHOR_TOKEN_CREDENTIALS_ID}")
                env('GIT_AUTHOR_BOT', "${GIT_BOT_AUTHOR_NAME}")
                env('BOT_CREDENTIALS_ID', "${GIT_BOT_AUTHOR_CREDENTIALS_ID}")

                env('MAVEN_DEPENDENCIES_REPOSITORY', "${MAVEN_ARTIFACTS_REPOSITORY}")
                env('MAVEN_DEPLOY_REPOSITORY', "${MAVEN_ARTIFACTS_REPOSITORY}")
                if (jobType == KogitoJobType.RELEASE) {
                    env('NEXUS_RELEASE_URL', "${MAVEN_NEXUS_RELEASE_URL}")
                    env('NEXUS_RELEASE_REPOSITORY_ID', "${MAVEN_NEXUS_RELEASE_REPOSITORY}")
                    env('NEXUS_STAGING_PROFILE_ID', "${MAVEN_NEXUS_STAGING_PROFILE_ID}")
                    env('NEXUS_BUILD_PROMOTION_PROFILE_ID', "${MAVEN_NEXUS_BUILD_PROMOTION_PROFILE_ID}")
                }
            }
        }
    }
}

void setupPromoteJob(String jobFolder, KogitoJobType jobType) {
    KogitoJobTemplate.createPipelineJob(this, getJobParams('optaplanner-promote', jobFolder, "${JENKINS_PATH}/Jenkinsfile.promote", 'Optaplanner Promote')).with {
        parameters {
            stringParam('DISPLAY_NAME', '', 'Setup a specific build display name')

            stringParam('BUILD_BRANCH_NAME', "${GIT_BRANCH}", 'Set the Git branch to checkout')

            // Deploy job url to retrieve deployment.properties
            stringParam('DEPLOY_BUILD_URL', '', 'URL to jenkins deploy build to retrieve the `deployment.properties` file. If base parameters are defined, they will override the `deployment.properties` information')

            // Release information which can override `deployment.properties`
            stringParam('PROJECT_VERSION', '', 'Override `deployment.properties`. Optional if not RELEASE. If RELEASE, cannot be empty.')
            stringParam('KOGITO_VERSION', '', 'Optional if not RELEASE. If RELEASE, cannot be empty.')

            stringParam('GIT_TAG', '', 'Git tag to set, if different from PROJECT_VERSION')
        }

        environmentVariables {
            env('RELEASE', jobType == KogitoJobType.RELEASE)
            env('JENKINS_EMAIL_CREDS_ID', "${JENKINS_EMAIL_CREDS_ID}")

            env('GIT_AUTHOR', "${GIT_AUTHOR_NAME}")

            env('AUTHOR_CREDS_ID', "${GIT_AUTHOR_CREDENTIALS_ID}")
            env('GITHUB_TOKEN_CREDS_ID', "${GIT_AUTHOR_TOKEN_CREDENTIALS_ID}")
            env('GIT_AUTHOR_BOT', "${GIT_BOT_AUTHOR_NAME}")
            env('BOT_CREDENTIALS_ID', "${GIT_BOT_AUTHOR_CREDENTIALS_ID}")

            env('MAVEN_SETTINGS_CONFIG_FILE_ID', "${MAVEN_SETTINGS_FILE_ID}")
            env('MAVEN_DEPENDENCIES_REPOSITORY', "${MAVEN_ARTIFACTS_REPOSITORY}")
            env('MAVEN_DEPLOY_REPOSITORY', "${MAVEN_ARTIFACTS_REPOSITORY}")

            env('PROPERTIES_FILE_NAME', 'deployment.properties')
            env('GITHUB_CLI_VERSION', '0.11.1')
        }
    }
}

void setupOptaPlannerTurtleTestsJob(String jobFolder) {
    def jobParams = getJobParams('optaplanner-turtle-tests', jobFolder, "${JENKINS_PATH}/Jenkinsfile.turtle",
            'Run OptaPlanner turtle tests on a weekly basis.')
    KogitoJobTemplate.createPipelineJob(this, jobParams).with {
        properties {
            pipelineTriggers {
                triggers {
                    cron {
                        spec('H H * * 5') // Run every Friday.
                    }
                }
            }
        }

        parameters {
            stringParam('BUILD_BRANCH_NAME', "${GIT_BRANCH}", 'Git branch to checkout')
            stringParam('GIT_AUTHOR', "${GIT_AUTHOR_NAME}", 'Git author or an organization.')
        }
    }
}
