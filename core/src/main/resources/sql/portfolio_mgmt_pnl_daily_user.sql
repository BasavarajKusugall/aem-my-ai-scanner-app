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
-- Table structure for table `pnl_daily_user`
--

DROP TABLE IF EXISTS `pnl_daily_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pnl_daily_user` (
  `pnl_user_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `as_of_date` date NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `total_equity_mv` decimal(20,8) NOT NULL DEFAULT '0.00000000',
  `total_deriv_mv` decimal(20,8) NOT NULL DEFAULT '0.00000000',
  `cash_balance` decimal(20,8) NOT NULL DEFAULT '0.00000000',
  `realized_pnl` decimal(20,8) NOT NULL DEFAULT '0.00000000',
  `unrealized_pnl` decimal(20,8) NOT NULL DEFAULT '0.00000000',
  `fees_taxes` decimal(20,8) NOT NULL DEFAULT '0.00000000',
  `net_liquidation` decimal(20,8) NOT NULL DEFAULT '0.00000000',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`pnl_user_id`),
  UNIQUE KEY `ux_pnl_user_date` (`user_id`,`as_of_date`),
  KEY `ix_pnl_user_date` (`as_of_date`),
  CONSTRAINT `fk_pnl_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pnl_daily_user`
--

LOCK TABLES `pnl_daily_user` WRITE;
/*!40000 ALTER TABLE `pnl_daily_user` DISABLE KEYS */;
/*!40000 ALTER TABLE `pnl_daily_user` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-08-20 20:22:00
