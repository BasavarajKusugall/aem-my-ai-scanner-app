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
-- Table structure for table `user_broker_account`
--

DROP TABLE IF EXISTS `user_broker_account`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_broker_account` (
  `account_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `broker_id` bigint unsigned NOT NULL,
  `broker_name` varchar(50) NOT NULL,
  `broker_account_ref` varchar(128) NOT NULL,
  `account_alias` varchar(128) DEFAULT NULL,
  `base_currency` char(3) NOT NULL DEFAULT 'INR',
  `api_key` varchar(100) DEFAULT NULL,
  `api_secret` varchar(100) DEFAULT NULL,
  `password` varchar(100) DEFAULT NULL,
  `pin` varchar(10) DEFAULT NULL,
  `request_token` varchar(512) DEFAULT NULL,
  `access_token` varchar(512) DEFAULT NULL,
  `last_token_refreshed` timestamp NULL DEFAULT NULL,
  `status` enum('ACTIVE','CLOSED') NOT NULL DEFAULT 'ACTIVE',
  `opened_at` datetime DEFAULT NULL,
  `closed_at` datetime DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `ux_user_broker_ref` (`user_id`,`broker_id`,`broker_account_ref`),
  UNIQUE KEY `uq_user_broker_account` (`user_id`,`broker_name`,`broker_account_ref`),
  KEY `ix_account_user` (`user_id`),
  KEY `ix_account_broker` (`broker_id`),
  CONSTRAINT `fk_uba_broker` FOREIGN KEY (`broker_id`) REFERENCES `broker` (`broker_id`),
  CONSTRAINT `fk_uba_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_broker_account`
--

LOCK TABLES `user_broker_account` WRITE;
/*!40000 ALTER TABLE `user_broker_account` DISABLE KEYS */;
INSERT INTO `user_broker_account` VALUES (1,1,1,'','45CAY4','Upstox Primary Account','INR',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'ACTIVE','2025-08-19 21:35:25',NULL,'2025-08-19 16:05:25'),(2,1,2,'','JS8737','Zerodha Kite Primary Account','INR','2wf4lhzop39crtwn','8x19vts1b41nma141y4xvdreldl4yi72',NULL,NULL,'R5uKnr0fJ0YYMGfmggWdoCSR7cLWrsYr',NULL,NULL,'ACTIVE','2025-08-19 22:44:54',NULL,'2025-08-19 17:14:54');
/*!40000 ALTER TABLE `user_broker_account` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-08-20 20:18:43
