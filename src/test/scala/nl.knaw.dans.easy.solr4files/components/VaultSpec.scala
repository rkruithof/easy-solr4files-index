/**
 * Copyright (C) 2017 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.solr4files.components

import nl.knaw.dans.easy.solr4files.TestSupportFixture

import scala.util.Success

class VaultSpec extends TestSupportFixture {

  "getStoreNames" should "return names" in {
    inside(mockVault("vaultStoreNames").getStoreNames) {
      case Success(names) => names should contain only("foo", "bar", "rabarbera", "barbapapa")
    }
  }

  "getBagIds" should "return UUID's" in {
    inside(mockVault("vaultBagIds").getBagIds("pdbs")) {
      case Success(names) => names should contain only(
        "9da0541a-d2c8-432e-8129-979a9830b427",
        "24d305fc-060c-4b3b-a5f5-9f212d463cbc",
        "3528bd4c-a87a-4bfa-9741-a25db7ef758a",
        "f70c19a5-0725-4950-aa42-6489a9d73806",
        "6ccadbad-650c-47ec-936d-2ef42e5f3cda")
    }
  }
}
