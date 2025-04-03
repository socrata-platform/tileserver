@Library('socrata-pipeline-library@7.0.0') _

commonPipeline(
    defaultBuildWorker: 'build-worker',
    jobName: 'tileserver',
    language: 'scala',
    languageOptions: [
        crossCompile: true,
    ],
    projects: [
        [
            name: 'tileserver',
            deploymentEcosystem: 'marathon-mesos',
            type: 'service',
            compiled: true,
            paths: [
                dockerBuildContext: 'docker'
            ]
        ]
    ],
    teamsChannelWebhookId: 'WORKFLOW_IQ',
)
