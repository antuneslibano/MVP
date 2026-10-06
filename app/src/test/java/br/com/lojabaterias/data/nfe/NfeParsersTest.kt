package br.com.lojabaterias.data.nfe

import br.com.lojabaterias.data.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NfeParsersTest {

    private val xml = """<?xml version="1.0" encoding="UTF-8"?>
<nfeProc xmlns="http://www.portalfiscal.inf.br/nfe" versao="4.00">
 <NFe><infNFe Id="NFe33260912345678000199550010000042001123456786" versao="4.00">
  <ide><cUF>33</cUF><nNF>4200</nNF><serie>1</serie><dhEmi>2026-09-28T10:15:00-03:00</dhEmi></ide>
  <emit><CNPJ>12345678000199</CNPJ><xNome>PCR BATERIAS BATERAX LTDA</xNome></emit>
  <det nItem="1"><prod><cProd>1020</cProd><xProd>BATERIA HELIAR HE60DD 60AH</xProd><qCom>4.0000</qCom>
   <vUnCom>706.66</vUnCom><vProd>2826.64</vProd><vDesc>56.53</vDesc></prod></det>
  <det nItem="2"><prod><cProd>2030</cProd><xProd>BAT MOURA M60AD</xProd><qCom>2.0000</qCom>
   <vUnCom>500.00</vUnCom><vProd>1000.00</vProd></prod></det>
  <total><ICMSTot><vProd>3826.64</vProd><vDesc>56.53</vDesc><vFrete>30.00</vFrete><vNF>3800.11</vNF></ICMSTot></total>
  <cobr><dup><nDup>001</nDup><dVenc>2026-10-28</dVenc><vDup>1900.06</vDup></dup>
        <dup><nDup>002</nDup><dVenc>2026-11-27</dVenc><vDup>1900.05</vDup></dup></cobr>
 </infNFe></NFe>
</nfeProc>"""

    @Test
    fun xmlIsReadExactly() {
        val d = NfeXmlParser.parse(xml)
        assertEquals("4200", d.number)
        assertEquals("PCR BATERIAS BATERAX LTDA", d.supplier)
        assertEquals(LocalDate.of(2026, 9, 28), d.issueDate)
        assertEquals(2, d.items.size)
        assertEquals(NfeItem("BATERIA HELIAR HE60DD 60AH", "1020", 4, 282_664, 5_653), d.items[0])
        assertEquals(380_011L, d.total)
        assertEquals(3_000L, d.extras) // frete
        assertEquals(listOf(190_006L, 190_005L), d.bills.map { it.amount })
        assertEquals(LocalDate.of(2026, 10, 28), d.bills[0].dueDate)
    }

    private val danfe = """
RECEBEMOS DE PCR BATERIAS BATERAX LTDA OS PRODUTOS E/OU SERVIÇOS CONSTANTES DA NOTA FISCAL ELETRÔNICA INDICADA ABAIXO
NF-e
Nº 000.004.200
SÉRIE 1
DANFE
CHAVE DE ACESSO
3326 0912 3456 7800 0199 5500 1000 0042 0011 2345 6786
NATUREZA DA OPERAÇÃO
VENDA DE MERCADORIA
DESTINATÁRIO / REMETENTE
NOME / RAZÃO SOCIAL CNPJ / CPF DATA DA EMISSÃO
ART DAS BATERIAS 12.345.678/0001-90 28/09/2026
FATURA / DUPLICATA
001 28/10/2026 1.385,06 002 27/11/2026 1.385,05
CÁLCULO DO IMPOSTO
BASE DE CÁLC. DO ICMS VALOR DO ICMS BASE DE CÁLC. ICMS S.T. VALOR DO ICMS SUBST. VALOR TOTAL DOS PRODUTOS
0,00 0,00 0,00 0,00 2.826,64
VALOR DO FRETE VALOR DO SEGURO DESCONTO OUTRAS DESPESAS VALOR TOTAL DO IPI VALOR TOTAL DA NOTA
0,00 0,00 56,53 0,00 0,00 2.770,11
DADOS DOS PRODUTOS / SERVIÇOS
CÓDIGO DESCRIÇÃO DO PRODUTO / SERVIÇO NCM/SH O/CST CFOP UN QUANT V.UNIT V.TOTAL BC.ICMS V.ICMS V.IPI ALIQ.ICMS ALIQ.IPI
1020 BATERIA HELIAR HE60DD 60AH 85071010 060 5405 UN 4,0000 706,6600 2.826,64 0,00 0,00 0,00 0,00 0,00
DADOS ADICIONAIS
INFORMAÇÕES COMPLEMENTARES
"""

    @Test
    fun danfeWithDiscountOnlyInTotals() {
        val d = DanfeTextParser.parse(danfe)
        assertEquals("4200", d.number)
        assertEquals("PCR BATERIAS BATERAX LTDA", d.supplier)
        assertEquals(LocalDate.of(2026, 9, 28), d.issueDate)
        assertEquals(listOf(NfeItem("BATERIA HELIAR HE60DD 60AH", "1020", 4, 282_664, 5_653)), d.items)
        assertEquals(277_011L, d.total)
        assertEquals(0L, d.extras)
        assertEquals(listOf(138_506L, 138_505L), d.bills.map { it.amount })
        assertEquals(LocalDate.of(2026, 11, 27), d.bills[1].dueDate)
    }

    private val danfe2 = """
RECEBEMOS DE OESTE RIO DISTRIBUIDORA DE BATERIAS LTDA OS PRODUTOS CONSTANTES DA NOTA FISCAL
NF-e Nº 000.004.201 Série 1
DATA DE EMISSÃO
05/10/2026
FATURA
Num. Venc. Valor
1 04/11/2026 R$ 1.000,00
2 04/12/2026 R$ 1.000,00
CÁLCULO DO IMPOSTO
VALOR TOTAL DA NOTA: 2.000,00
DADOS DOS PRODUTOS / SERVIÇOS
CÓD. DESCRIÇÃO NCM/SH CST CFOP UN QTD V.UNIT V.TOTAL V.DESC BC.ICMS V.ICMS
M60AD BATERIA MOURA 85071010 060 5405 UN 2,0000 520,0000 1.040,00 40,00 0,00 0,00
M60AD 60AH
Z60D BATERIA ZETTA Z60D 85071010 060 5405 UN 2 500,0000 1.000,00 0,00 0,00 0,00
DADOS ADICIONAIS
"""

    @Test
    fun danfeWithDiscountColumnAndWrappedDescription() {
        val d = DanfeTextParser.parse(danfe2)
        assertEquals("4201", d.number)
        assertEquals(LocalDate.of(2026, 10, 5), d.issueDate)
        assertEquals(2, d.items.size)
        assertEquals(NfeItem("BATERIA MOURA M60AD 60AH", "M60AD", 2, 104_000, 4_000), d.items[0])
        assertEquals(NfeItem("BATERIA ZETTA Z60D", "Z60D", 2, 100_000, 0), d.items[1])
        assertEquals(200_000L, d.total)
        assertEquals(2, d.bills.size)
        assertEquals("Oeste Rio Distribuidora Moura", NfeMatcher.supplierName(d.supplier))
    }

    @Test
    fun accessKeyNeedsValidCheckDigit() {
        assertEquals("33260912345678000199550010000042001123456786", DanfeTextParser.accessKey("x 3326 0912 3456 7800 0199 5500 1000 0042 0011 2345 6786 y"))
        assertNull(DanfeTextParser.accessKey("3326 0912 3456 7800 0199 5500 1000 0042 0011 2345 6787"))
    }

    private fun product(model: String, id: Long) =
        Product(id = id, model = model, cost = 0, pricePix = 0, priceDebit = 0, priceCredit = 0, stock = 0)

    @Test
    fun matchesProductsFromDescriptions() {
        val products = listOf(product("HE60DD", 1), product("HE60D", 2), product("BE50D", 3), product("M60AD", 4))
        assertEquals(1L, NfeMatcher.match("1020 BATERIA HELIAR HE60DD 60AH", products)?.id)
        assertEquals(2L, NfeMatcher.match("BAT HELIAR HE60D", products)?.id)
        assertEquals(1L, NfeMatcher.match("BAT HE-60DD", products)?.id)
        assertEquals(4L, NfeMatcher.match("M60AD BATERIA MOURA", products)?.id)
        assertNull(NfeMatcher.match("BATERIA ACDELCO 70AH", products))
        assertEquals(282_664L, NfeText.money("2.826,64"))
        assertEquals(282_664L, NfeText.money("2826.64"))
        assertEquals(4, NfeText.quantity("4,0000"))
        assertEquals("PCR Baterias Baterax", NfeMatcher.supplierName("PCR BATERIAS BATERAX LTDA"))
        assertEquals("Distribuidora São Jorge Ltda", NfeMatcher.supplierName("DISTRIBUIDORA SÃO JORGE LTDA"))
    }
}
