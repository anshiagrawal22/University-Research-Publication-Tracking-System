pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven3'
        nodejs 'Node20'
    }

    environment {
        BACKEND_IMAGE  = 'publication-portal-backend'
        FRONTEND_IMAGE = 'publication-portal-frontend'
        BACKEND_PORT   = '8082'
        FRONTEND_PORT  = '3000'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Frontend install & audit') {
            steps {
                dir('frontend') {
                    sh 'npm ci'
                }
            }
        }

        stage('Security Gate — Code & Dependencies') {
            steps {
                sh 'bash scripts/security-scan.sh'
            }
        }

        stage('Backend build & test') {
            steps {
                sh 'mvn -B -f backend/pom.xml clean verify'
            }
        }

        stage('Frontend production build') {
            steps {
                dir('frontend') {
                    sh 'npm run build'
                }
            }
        }

        stage('Docker image creation') {
            steps {
                sh """
                    docker compose build
                    docker tag ${BACKEND_IMAGE}:latest ${BACKEND_IMAGE}:${BUILD_NUMBER}
                    docker tag ${FRONTEND_IMAGE}:latest ${FRONTEND_IMAGE}:${BUILD_NUMBER}
                """
            }
        }

        stage('Security Gate — Container scan') {
            steps {
                sh """
                    if command -v trivy >/dev/null 2>&1; then
                        trivy image --exit-code 1 --severity CRITICAL --ignore-unfixed ${BACKEND_IMAGE}:${BUILD_NUMBER}
                        trivy image --exit-code 1 --severity CRITICAL --ignore-unfixed ${FRONTEND_IMAGE}:${BUILD_NUMBER}
                    else
                        echo "Trivy CLI not installed on Jenkins agent; skipping image CVE scan."
                    fi
                """
            }
        }

        stage('Deploy containers & verify') {
            steps {
                sh 'docker compose up -d --remove-orphans'
                sh 'bash scripts/verify-deployment.sh'
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'backend/target/surefire-reports/*.xml'
        }
        failure {
            sh 'docker compose logs --tail=50 || true'
        }
        success {
            archiveArtifacts artifacts: 'backend/target/publication-portal.jar,frontend/dist/**',
                fingerprint: true
        }
    }
}
