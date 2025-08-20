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
-- Table structure for table `execution`
--

DROP TABLE IF EXISTS `execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `execution` (
  `execution_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `order_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `instrument_id` bigint unsigned NOT NULL,
  `side` enum('BUY','SELL') NOT NULL,
  `exec_qty` decimal(20,8) NOT NULL,
  `exec_price` decimal(20,8) NOT NULL,
  `exec_fees` decimal(20,8) NOT NULL DEFAULT '0.00000000',
  `exec_taxes` decimal(20,8) NOT NULL DEFAULT '0.00000000',
  `exec_time` datetime NOT NULL,
  `broker_trade_id` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`execution_id`),
  KEY `ix_exec_order` (`order_id`),
  KEY `ix_exec_acct_time` (`account_id`,`exec_time`),
  KEY `ix_exec_instr` (`instrument_id`),
  CONSTRAINT `fk_exec_account` FOREIGN KEY (`account_id`) REFERENCES `user_broker_account` (`account_id`),
  CONSTRAINT `fk_exec_instrument` FOREIGN KEY (`instrument_id`) REFERENCES `instrument` (`instrument_id`),
  CONSTRAINT `fk_exec_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `execution`
--

LOCK TABLES `execution` WRITE;
/*!40000 ALTER TABLE `execution` DISABLE KEYS */;
/*!40000 ALTER TABLE `execution` ENABLE KEYS */;
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
