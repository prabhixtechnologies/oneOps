package com.prabhix.platform.mail.provisioning;

import com.prabhix.platform.mail.dto.DomainDtos;

import java.util.ArrayList;
import java.util.List;

public final class DnsRecordBuilder {

    private static final String MAIL_HOST = "mail.prabhixtechnologies.com";

    private DnsRecordBuilder() {
    }

    public static List<DomainDtos.DnsRecord> build(String domain, String dkimSelector,
                                                   String dkimPublicKey, String verificationToken) {
        List<DomainDtos.DnsRecord> records = new ArrayList<>();
        records.add(new DomainDtos.DnsRecord(domain, "MX", "10 " + MAIL_HOST, null, "PENDING"));
        records.add(new DomainDtos.DnsRecord(domain, "TXT",
                "v=spf1 mx a:" + MAIL_HOST + " -all", null, "PENDING"));
        records.add(new DomainDtos.DnsRecord(
                dkimSelector + "._domainkey." + domain, "TXT",
                "v=DKIM1; k=rsa; p=" + stripPem(dkimPublicKey), null, "PENDING"));
        records.add(new DomainDtos.DnsRecord("_dmarc." + domain, "TXT",
                "v=DMARC1; p=quarantine; rua=mailto:dmarc@" + domain + "; pct=100", null, "PENDING"));
        records.add(new DomainDtos.DnsRecord("mail." + domain, "A", MAIL_HOST, null, "PENDING"));
        records.add(new DomainDtos.DnsRecord("_mta-sts." + domain, "TXT",
                "v=STSv1; id=" + System.currentTimeMillis(), null, "PENDING"));
        records.add(new DomainDtos.DnsRecord(domain, "TXT",
                "prabhix-verification=" + verificationToken, null, "PENDING"));
        return records;
    }

    private static String stripPem(String pem) {
        if (pem == null) {
            return "";
        }
        return pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
    }
}
