/**
 * Copyright (C) 2015-2016 - All rights reserved.
 * This file is part of the PathDB project which is released under the GPLv3 license.
 * See file LICENSE.txt or go to http://www.gnu.org/licenses/gpl.txt for full license details.
 * You may use, distribute and modify this code under the terms of the GPLv3 license.
 */

package pathdb.kpathindex.pathindex.tree;

public class SplitResult {
  public Long[] key = null;
  public long[] primkey = null;
  public long left;
  public long right;

  public SplitResult() {
  }
}