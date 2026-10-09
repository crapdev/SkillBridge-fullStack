#!/bin/bash
cd /workspace
mvn test -Dtest=AuthServiceTest
mvn test -Dtest=AiRecommendationServiceTest
