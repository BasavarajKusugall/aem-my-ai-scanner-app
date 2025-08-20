-- MySQL dump 10.13  Distrib 8.0.42, for macos15 (arm64)
--
-- Host: localhost    Database: portfolio_mgmt
-- ------------------------------------------------------
-- Server version	9.3.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `broker_token`
--

DROP TABLE IF EXISTS `broker_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `broker_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `broker_account_id` bigint unsigned NOT NULL,
  `broker_name` varchar(50) NOT NULL,
  `access_token` varchar(512) DEFAULT NULL,
  `refresh_token` varchar(512) DEFAULT NULL,
  `token_expiry` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_broker` (`user_id`,`broker_account_id`,`broker_name`),
  KEY `fk_broker_token_account` (`broker_account_id`),
  CONSTRAINT `fk_broker_token_account` FOREIGN KEY (`broker_account_id`) REFERENCES `user_broker_account` (`account_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_broker_token_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `broker_token`
--

LOCK TABLES `broker_token` WRITE;
/*!40000 ALTER TABLE `broker_token` DISABLE KEYS */;
INSERT INTO `broker_token` VALUES (1,1,1,'UPSTOX','eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiI0NUNBWTQiLCJqdGkiOiI2OGE0YTBhMWM1MDFlYjUyMzRhNDE2YjMiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6dHJ1ZSwiaWF0IjoxNzU1NjE5NDg5LCJpc3MiOiJ1ZGFwaS1nYXRld2F5LXNlcnZpY2UiLCJleHAiOjE3NTU2NDA4MDB9.n2tcMmK7TGSmEcpPIvMQL2ReTJcHk6rxUy-sQCYWdDk','eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiI0NUNBWTQiLCJqdGkiOiI2OGE0YTBhMWM1MDFlYjUyMzRhNDE2YjMiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6dHJ1ZSwiaWF0IjoxNzU1NjE5NDg5LCJpc3MiOiJ1ZGFwaS1nYXRld2F5LXNlcnZpY2UiLCJleHAiOjE3NTU2NDA4MDB9.n2tcMmK7TGSmEcpPIvMQL2ReTJcHk6rxUy-sQCYWdDk','2025-08-20 17:08:20','2025-08-19 17:08:20','2025-08-19 17:08:20'),(2,1,2,'ZERODHA','r5P6scK376DbYqdgNml62Xc03V9r6i2o','3dhIq4leh9HtxOVcui7jVYhktxM3eVqD','2025-08-20 17:15:36','2025-08-19 17:15:36','2025-08-20 12:44:47');
/*!40000 ALTER TABLE `broker_token` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-08-20 20:21:59
