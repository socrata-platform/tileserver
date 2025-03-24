@Library('socrata-pipeline-library@3.0.0') _

commonPipeline(
    defaultBuildWorker: 'build-worker',
    jobName: 'tileserver',
    language: 'scala',
    languageVersion: '2.12',
    projects: [
        [
            name: 'tileserver',
            deploymentEcosystem: 'marathon-mesos',
            type: 'service',
        ]
    ],
    teamsChannelWebhookId: 'WORKFLOW_IQ',
)
