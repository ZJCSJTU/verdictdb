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

/*
// Licensed to Julian Hyde under one or more contributor license
// agreements. See the NOTICE file distributed with this work for
// additional information regarding copyright ownership.
//
// Julian Hyde licenses this file to you under the Modified BSD License
// (the "License"); you may not use this file except in compliance with
// the License. You may obtain a copy of the License at:
//
// http://opensource.org/licenses/BSD-3-Clause
*/
package sqlline;

import java.util.List;

import jline.console.completer.Completer;

/**
 * A generic command to be executed. Execution of the command should be
 * dispatched to the
 * {@link #execute(String, DispatchCallback)} method after
 * determining that the command is appropriate with the
 * {@link #matches(String)} method.
 */
interface CommandHandler {
  /**
   * @return the name of the command
   */
  String getName();

  /**
   * @return all the possible names of this command.
   */
  List<String> getNames();

  /**
   * @return the short help description for this command.
   */
  String getHelpText();

  /**
   * Checks to see if the specified string can be dispatched to this
   * command.
   *
   * @param line The command line to check
   * @return the command string that matches, or null if it no match
   */
  String matches(String line);

  /**
   * Executes the specified command.
   *
   * @param line The full command line to execute
   * @param dispatchCallback the callback to check or interrupt the action
   */
  void execute(String line, DispatchCallback dispatchCallback);

  /**
   * Returns the completers that can handle parameters.
   *
   * @return Completers that can handle parameters
   */
  List<Completer> getParameterCompleters();
}

// End CommandHandler.java
