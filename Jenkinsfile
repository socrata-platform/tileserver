@Library('socrata-pipeline-library@9.7.0') _

commonPipeline(
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
    teamsChannelWebhookId: 'WORKFLOW_EGRESS_AUTOMATION',
)
