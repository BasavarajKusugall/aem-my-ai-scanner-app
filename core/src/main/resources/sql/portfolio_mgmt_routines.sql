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
-- Temporary view structure for view `v_account_holdings_snapshot`
--

DROP TABLE IF EXISTS `v_account_holdings_snapshot`;
/*!50001 DROP VIEW IF EXISTS `v_account_holdings_snapshot`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_account_holdings_snapshot` AS SELECT 
 1 AS `account_id`,
 1 AS `instrument_id`,
 1 AS `symbol`,
 1 AS `instrument_type`,
 1 AS `qty`,
 1 AS `avg_cost`,
 1 AS `last_price`,
 1 AS `cost_value`,
 1 AS `market_value`,
 1 AS `unrealized_pnl`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `v_account_positions_snapshot`
--

DROP TABLE IF EXISTS `v_account_positions_snapshot`;
/*!50001 DROP VIEW IF EXISTS `v_account_positions_snapshot`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_account_positions_snapshot` AS SELECT 
 1 AS `account_id`,
 1 AS `instrument_id`,
 1 AS `symbol`,
 1 AS `instrument_type`,
 1 AS `product_type`,
 1 AS `direction`,
 1 AS `open_qty`,
 1 AS `avg_entry_price`,
 1 AS `last_price`,
 1 AS `unrealized_pnl`,
 1 AS `realized_pnl`*/;
SET character_set_client = @saved_cs_client;

--
-- Final view structure for view `v_account_holdings_snapshot`
--

/*!50001 DROP VIEW IF EXISTS `v_account_holdings_snapshot`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `v_account_holdings_snapshot` AS select `h`.`account_id` AS `account_id`,`h`.`instrument_id` AS `instrument_id`,`i`.`symbol` AS `symbol`,`i`.`instrument_type` AS `instrument_type`,`h`.`qty` AS `qty`,`h`.`avg_cost` AS `avg_cost`,`pl`.`last_price` AS `last_price`,(`h`.`qty` * `h`.`avg_cost`) AS `cost_value`,(`h`.`qty` * `pl`.`last_price`) AS `market_value`,((`pl`.`last_price` - `h`.`avg_cost`) * `h`.`qty`) AS `unrealized_pnl` from ((`holding` `h` join `instrument` `i` on((`i`.`instrument_id` = `h`.`instrument_id`))) left join `price_last` `pl` on((`pl`.`instrument_id` = `h`.`instrument_id`))) */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `v_account_positions_snapshot`
--

/*!50001 DROP VIEW IF EXISTS `v_account_positions_snapshot`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `v_account_positions_snapshot` AS select `p`.`account_id` AS `account_id`,`p`.`instrument_id` AS `instrument_id`,`i`.`symbol` AS `symbol`,`i`.`instrument_type` AS `instrument_type`,`p`.`product_type` AS `product_type`,`p`.`direction` AS `direction`,`p`.`open_qty` AS `open_qty`,`p`.`avg_entry_price` AS `avg_entry_price`,`pl`.`last_price` AS `last_price`,(case when (`p`.`direction` = 'LONG') then ((`pl`.`last_price` - `p`.`avg_entry_price`) * `p`.`open_qty`) when (`p`.`direction` = 'SHORT') then ((`p`.`avg_entry_price` - `pl`.`last_price`) * `p`.`open_qty`) else 0 end) AS `unrealized_pnl`,`p`.`realized_pnl` AS `realized_pnl` from ((`position` `p` join `instrument` `i` on((`i`.`instrument_id` = `p`.`instrument_id`))) left join `price_last` `pl` on((`pl`.`instrument_id` = `p`.`instrument_id`))) where (`p`.`status` = 'OPEN') */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-08-20 20:22:01
