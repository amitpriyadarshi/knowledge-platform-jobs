package org.sunbird.job.fixture

object EventFixture {

  val EVENT_1: String =
    """
      |{"eid":"BE_JOB_REQUEST","ets":1563788371969,"mid":"LMS.1563788371969.590c5fa0-0ce8-46ed-bf6c-681c0a1fdac8","actor":{"type":"System","id":"Certificate Generator"},"context":{"pdata":{"ver":"1.0","id":"org.sunbird.platform"}},"object":{"type":"GenerateCertificate","id":"874ed8a5-782e-4f6c-8f36-e0288455901e"},"edata":{"svgTemplate":"https://ntpstagingall.blob.core.windows.net/user/cert/File-01311849840255795242.svg","courseName":"new course may23","data":[{"recipientName":"Creation ","recipientId":"874ed8a5-782e-4f6c-8f36-e0288455901e"}],"name":"100PercentCompletionCertificate","tag":"0125450863553740809","issuer":{"name":"Gujarat Council of Educational Research and Training","url":"https://gcert.gujarat.gov.in/gcert/","publicKey":["1","2"]},"signatoryList":[{"name":"CEO Gujarat","id":"CEO","designation":"CEO","image":"https://cdn.pixabay.com/photo/2014/11/09/08/06/signature-523237__340.jpg"}],"criteria":{"narrative":"course completion certificate"},"basePath":"https://dev.sunbirded.org/certs","related":{"type":"course","batchId":"123","courseId":"543535"}}}
      |""".stripMargin

  val EVENT_2: String =
    """
      |{"eid":"BE_JOB_REQUEST","ets":1563788371969,"mid":"LMS.1563788371969.590c5fa0-0ce8-46ed-bf6c-681c0a1fdac8","actor":{"type":"System","id":"Certificate Generator"},"context":{"pdata":{"ver":"1.0","id":"org.sunbird.platform"}},"object":{"type":"GenerateCertificate","id":"874ed8a5-782e-4f6c-8f36-e0288455901e"},"edata":{"svgTemplate":"","courseName":"new course may23","data":[{"recipientName":"Creation ","recipientId":"874ed8a5-782e-4f6c-8f36-e0288455901e"}],"name":"100PercentCompletionCertificate","tag":"0125450863553740809","issuer":{"name":"Gujarat Council of Educational Research and Training","url":"https://gcert.gujarat.gov.in/gcert/","publicKey":["1","2"]},"signatoryList":[{"name":"CEO Gujarat","id":"CEO","designation":"CEO","image":"https://cdn.pixabay.com/photo/2014/11/09/08/06/signature-523237__340.jpg"}],"criteria":{"narrative":"course completion certificate"},"basePath":"https://dev.sunbirded.org/certs","related":{"type":"course","batchId":"123","courseId":"543535"}}}
      |""".stripMargin
}
