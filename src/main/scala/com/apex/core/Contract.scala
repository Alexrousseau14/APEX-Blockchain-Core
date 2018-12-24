/*
 *
 *
 *
 *
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: Contract.scala
 *
 * @author: ruixiao.xiao@chinapex.com: 18-12-13 上午14:32@version: 1.0
 */

package com.apex.core

import java.io.{DataInputStream, DataOutputStream}

import com.apex.crypto.Ecdsa.PublicKey

class Contract(val account: PublicKey,
               val code: Array[Byte],
               val name: String = "",
               val author: String = "",
               val email: String = "",
               val description: String = "") extends com.apex.common.Serializable {

  import Contract._

  override def serialize(os: DataOutputStream): Unit = {
    import com.apex.common.Serializable._
    os.writeInt(version)
    os.write(account)
    os.writeByteArray(code)
    os.writeString(name)
    os.writeString(author)
    os.writeString(email)
    os.writeString(description)
  }
}

object Contract {
  private val version: Int = 0x01

  def deserialize(is: DataInputStream): Contract = {
    import com.apex.common.Serializable._
    // TODO check version
    is.readInt
    new Contract(
      account = is.readObj[PublicKey],
      code = is.readByteArray,
      name = is.readString(),
      author = is.readString(),
      email = is.readString(),
      description = is.readString()
    )
  }
}