/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.umich.verdict;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Scanner;

public class BaseIT {

    protected double error = 0.05;

    protected double samplingRatio = 0.10;

    protected static Connection conn;

    protected static Statement stmt;

    protected static VerdictJDBCContext vc;

    public static String readHost() throws FileNotFoundException {
        ClassLoader classLoader = BaseIT.class.getClassLoader();
        File file = new File(classLoader.getResource("integration_test_host.test").getFile());

        Scanner scanner = new Scanner(file);
        String line = scanner.nextLine();
        scanner.close();
        return line;
    }
}
