-- Le descrizioni dei 34 prodotti in archivio: che cos'è lo strumento e, per un
-- fondo o un ETF, che cosa contiene davvero. Le legge la ℹ️ accanto al nome del
-- prodotto in `finanza.html` e in `situazione-teresa.html`.
--
-- ⚠️ Si scrive SOLO dove la descrizione manca (`description IS NULL`): una
-- descrizione corretta a mano dall'app è più vera di questa, e un UPDATE cieco
-- la riscriverebbe in silenzio alla prima riesecuzione. È anche ciò che rende il
-- file rieseguibile senza pensarci.
--
-- ⚠️ Il confronto è sul SIMBOLO, che è la chiave unica per utente
-- (`UNIQUE (user_id, symbol)`), non sull'ISIN: le crypto un ISIN non ce l'hanno,
-- e su un JOIN per ISIN sarebbero sei righe che non agganciano niente senza che
-- niente lo dica.
--
-- ⚠️ Quello che una descrizione NON deve contenere: prezzi, valori e giudizi di
-- convenienza. Sono cose che cambiano, e qui resterebbero scritte per sempre.
-- Dove il rischio è nella natura dello strumento (concentrazione settoriale,
-- durata lunga, breakeven inflation) lo si dice, perché quella è una proprietà
-- del titolo e non una previsione.
--
-- Le tre righe da rileggere quando si aggiorna questo file:
--   • INFU non è un fondo di obbligazioni indicizzate — è un breakeven, cioè una
--     posizione lunga TIPS e corta Treasury nominali. Ha duration quasi nulla e
--     non si muove come un fondo obbligazionario;
--   • GOOG e GOOGL sono la stessa società: cambia il diritto di voto;
--   • SPCX non è quotata in borsa, quindi da nessuna fonte di `get-prices`
--     arriverà mai un prezzo per lei.

DO $$
DECLARE
  v_user     uuid;
  v_scritte  integer;
BEGIN
  SELECT id INTO v_user FROM auth.users WHERE lower(email) = 'garsal1971@gmail.com' LIMIT 1;
  IF v_user IS NULL THEN
    -- Progetto dev o database appena creato: non c'è a chi intestare le righe.
    -- Meglio saltare che far fallire il deploy dell'intera migration.
    RAISE NOTICE 'fnz_products: utente garsal1971@gmail.com non trovato, descrizioni non scritte.';
    RETURN;
  END IF;

  UPDATE fnz_products p
     SET description = d.testo
    FROM (VALUES

  -- ══ Azioni ═════════════════════════════════════════════════════════════
  ('AAPL', $d$Azione di Apple Inc. (Nasdaq). Elettronica di consumo: iPhone — da solo poco meno di metà del fatturato — Mac, iPad, Apple Watch e AirPods, più una divisione Servizi in crescita costante (App Store, iCloud, Apple Music, pubblicità) che ha margini molto più alti dell'hardware. La marginalità dipende dal prezzo medio dell'iPhone e dal peso dei Servizi; il rischio principale è la concentrazione su un solo prodotto e la produzione appaltata quasi tutta in Asia.$d$),

  ('MSFT', $d$Azione di Microsoft Corp. (Nasdaq). Tre gambe: cloud Azure (la parte che cresce e che pesa di più sulla valutazione), software per le aziende e per le persone (Microsoft 365, Windows, Dynamics), e il resto — LinkedIn, Xbox, pubblicità di Bing. È il socio industriale di OpenAI e vende l'intelligenza artificiale come funzione a pagamento dentro i suoi prodotti (Copilot). Ricavi in gran parte ad abbonamento, quindi ricorrenti e prevedibili.$d$),

  ('GOOGL', $d$Azione di Alphabet Inc. classe A (Nasdaq). ⚠️ Stessa società di GOOG: la classe A dà UN VOTO per azione, la classe C nessuno — è l'unica differenza, e le due quotazioni si muovono quasi identiche. Alphabet è la holding di Google: ricerca e YouTube (la pubblicità è ancora la gran parte dei ricavi), Android, Google Cloud, e le scommesse lunghe — Waymo per la guida autonoma, DeepMind e i modelli Gemini. Il rischio grosso non è industriale ma legale: le cause antitrust su ricerca e pubblicità.$d$),

  ('GOOG', $d$Azione di Alphabet Inc. classe C (Nasdaq). ⚠️ Stessa società di GOOGL, stessi identici diritti economici: la classe C è SENZA DIRITTO DI VOTO (nacque nel 2014 da uno split per emettere azioni senza diluire il controllo dei fondatori, che tengono la classe B da dieci voti). Il business è quello di Alphabet: ricerca Google e YouTube — la pubblicità è ancora la gran parte dei ricavi — Android, Google Cloud, Waymo, DeepMind e i modelli Gemini.$d$),

  ('AMZN', $d$Azione di Amazon.com Inc. (Nasdaq). Due aziende dentro una: il commercio elettronico con la sua logistica (grandi ricavi, margini sottili) e AWS, il primo fornitore mondiale di cloud, che fa una minoranza del fatturato e la maggior parte dell'utile operativo. Accanto ci sono la pubblicità sul sito — ormai la terza per dimensione al mondo — e gli abbonamenti Prime. Chi la guarda guarda AWS: è lì che si decide il risultato.$d$),

  ('META', $d$Azione di Meta Platforms Inc. (Nasdaq). Le applicazioni — Facebook, Instagram, WhatsApp, Messenger, Threads — dove praticamente tutti i ricavi vengono dalla pubblicità profilata, e Reality Labs, la divisione di visori e occhiali intelligenti, che perde parecchi miliardi l'anno per scommessa dichiarata. Grande investimento in centri di calcolo per l'IA (modelli Llama). Il rischio ricorrente è regolatorio: privacy e trattamento dei dati in Europa.$d$),

  ('NVDA', $d$Azione di NVIDIA Corp. (Nasdaq). Progetta — non produce: la fabbricazione è appaltata, in gran parte a TSMC — i processori grafici e i sistemi che oggi fanno girare quasi tutto l'addestramento dell'intelligenza artificiale. La parte più grande dei ricavi viene dai centri dati; restano il gaming (GeForce), l'automotive e la grafica professionale. Il fossato vero non è il chip ma CUDA, l'ambiente software su cui il settore ha imparato a lavorare. Domanda concentrata in pochissimi clienti, quindi ricavi molto sensibili ai loro piani di spesa.$d$),

  ('TSM', $d$ADR di Taiwan Semiconductor Manufacturing Co. (NYSE). ⚠️ È una RICEVUTA DI DEPOSITO, non l'azione: un ADR TSM rappresenta 5 azioni ordinarie quotate a Taipei, e la banca depositaria trattiene una piccola commissione sui dividendi. TSMC è la maggiore fonderia di semiconduttori al mondo — non progetta chip propri, li produce su commessa per Apple, NVIDIA, AMD, Qualcomm — ed è di fatto sola sui nodi più avanzati. Il rischio che la distingue da ogni altra è geopolitico: gli impianti che contano stanno a Taiwan.$d$),

  ('MU', $d$Azione di Micron Technology Inc. (Nasdaq). Uno dei tre produttori mondiali di memorie: DRAM (la gran parte del giro d'affari) e NAND flash, più la HBM che si monta sugli acceleratori per l'IA. ⚠️ È un settore CICLICO nel modo più letterale: il prodotto è una merce indifferenziata, il prezzo lo fa l'equilibrio fra capacità installata e domanda, e i risultati passano da utili record a perdite nel giro di pochi trimestri. Si guarda il ciclo dei prezzi delle memorie, non il singolo trimestre.$d$),

  ('LLY', $d$Azione di Eli Lilly and Co. (NYSE). Farmaceutica statunitense fondata nel 1876. Il baricentro è oggi la classe dei GLP-1 per diabete e obesità (tirzepatide, venduta come Mounjaro e Zepbound), accanto a oncologia, immunologia e neuroscienze — con il farmaco per l'Alzheimer donanemab. Come ogni casa farmaceutica dipende da pochi brevetti e dal loro calendario di scadenza, e dalla pressione sui prezzi dei rimborsi pubblici americani.$d$),

  ('BSX', $d$Azione di Boston Scientific Corp. (NYSE). Dispositivi medici, non farmaci: cardiologia interventistica (stent, valvole), elettrofisiologia e ablazione, cardiostimolatori, endoscopia, urologia e neuromodulazione. Ricavi molto frammentati fra prodotti e ospedali, quindi senza il rischio-brevetto della farmaceutica; crescono col numero di procedure fatte, e la concorrenza si gioca sull'adozione clinica di ogni singolo dispositivo.$d$),

  ('PYPL', $d$Azione di PayPal Holdings Inc. (Nasdaq). Pagamenti digitali: il portafoglio PayPal, Venmo negli Stati Uniti, Braintree per i pagamenti dei grandi commercianti, più il «paga in tre rate». Guadagna una percentuale sul valore transato, quindi il numero da guardare sono i volumi e il margine per transazione — che la concorrenza di Apple Pay, delle carte e dei circuiti locali comprime da anni. Business maturo, esposto ai consumi discrezionali.$d$),

  ('RY4C', $d$Azione di Ryanair Holdings plc. ⚠️ Stessa società di RYA (Euronext Dublino) e dell'ADR RYAAY (Nasdaq): RY4C è la quotazione tedesca su Xetra, in euro — cambia la borsa, non il titolo. È la prima compagnia aerea europea per passeggeri, low cost puro: flotta di soli Boeing 737 per abbattere i costi di manutenzione e addestramento, aeroporti secondari, ricavi accessori (bagagli, posti, priorità) che pesano quanto il biglietto. I margini dipendono da carburante, coperture sul cherosene e consegne Boeing.$d$),

  ('NXTT', $d$Azione di Next Technology Holding Inc. (Nasdaq), ex WeTrade Group. ⚠️ MICROCAPITALIZZAZIONE ALTAMENTE SPECULATIVA, non una società paragonabile alle altre di questo elenco: capitalizzazione minima, scambi sottili, storia societaria fatta di cambi di nome e di attività — dal software per il commercio elettronico in Cina ai servizi di intelligenza artificiale — ed è nota soprattutto per tenere bitcoin in bilancio, il che ne lega il prezzo all'andamento della crypto più che ai propri ricavi. Su titoli così il prezzo può muoversi di decine di punti in una seduta e il rischio di perdita totale è reale.$d$),

  ('SPCX', $d$Azioni ordinarie di classe A di Space Exploration Technologies Corp. (SpaceX). ⚠️ SOCIETÀ NON QUOTATA IN BORSA: queste azioni non hanno un mercato pubblico, si comprano e si vendono sul secondario privato e la valutazione la fanno i round di finanziamento, non un listino. Ne discende una cosa pratica: `get-prices` non troverà mai un prezzo per questo simbolo da nessuna fonte, e il valore in Finanza resta quello di carico finché non lo si aggiorna a mano. L'attività è il lancio spaziale (Falcon 9, Falcon Heavy, Starship) e soprattutto Starlink, la costellazione di satelliti per l'accesso a internet, che è la parte con ricavi ricorrenti.$d$),

  -- ══ ETF azionari ═══════════════════════════════════════════════════════
  ('SMEA', $d$ETF azionario — iShares Core MSCI Europe UCITS ETF EUR Acc (ISIN IE00B4K48X80, Xetra). Contiene circa 400 società a grande e media capitalizzazione di 15 paesi europei sviluppati: pesano di più Regno Unito, Francia, Svizzera e Germania, e in cima ci sono i soliti nomi del listino europeo — farmaceutica svizzera, lusso francese, semiconduttori olandesi, banche. AD ACCUMULAZIONE: i dividendi vengono reinvestiti dentro il fondo, non distribuiti. Replica fisica a campionamento ottimizzato (compra i titoli veri, non tutti). È un mattone «core»: un solo strumento per l'azionario Europa.$d$),

  ('ITAMID', $d$ETF azionario — Amundi FTSE Italia PMI PIR 2020 UCITS ETF Acc (ISIN FR0011758085, ex Lyxor FTSE Italia Mid Cap PIR). Contiene le medie e piccole imprese italiane quotate fuori dal FTSE MIB: industria, banche del territorio, moda, meccanica. ⚠️ È COSTRUITO PER ESSERE PIR-COMPLIANT — rispetta i vincoli di composizione dei Piani Individuali di Risparmio — ed è la ragione del tag TASSAZIONE «PIR»: dentro un PIR e tenuto cinque anni, i guadagni non pagano l'imposta del 26 %. Fuori da un PIR quel vantaggio non esiste. Ad accumulazione. Mercato sottile: le PMI italiane si muovono più del listino grande, in su e in giù.$d$),

  ('CHIP', $d$ETF azionario settoriale — Amundi MSCI Semiconductors ESG Screened UCITS ETF (ISIN LU1900066033, ex Lyxor, su ETFplus). Contiene i produttori di semiconduttori e di apparecchiature per produrli di tutto il mondo: NVIDIA, TSMC, Broadcom, ASML, AMD, Applied Materials e simili. ⚠️ È un paniere DI UN SETTORE SOLO, e per giunta di un settore che si concentra in pochissimi nomi: le prime posizioni pesano moltissimo, quindi non è un modo per diversificare — è una scommessa sui chip fatta con un solo strumento invece che con dieci azioni. Ad accumulazione.$d$),

  ('DRAM', $d$ETF azionario tematico — Defiance Memory UCITS ETF Accumulating (ISIN IE000CEUZ052, Borsa Italiana). Contiene i produttori di memorie per calcolatori — DRAM e NAND: Micron, SK Hynix, Samsung, Kioxia, SanDisk/Western Digital — e la loro catena di fornitura di macchinari e materiali. ⚠️ Concentratissimo e CICLICO: le memorie sono una merce indifferenziata il cui prezzo oscilla di molto fra abbondanza e scarsità di capacità produttiva, e il fondo ne segue il ciclo per intero. Ad accumulazione. Si sovrappone in parte a CHIP e a MU, che stanno nello stesso mestiere.$d$),

  -- ══ ETF obbligazionari ═════════════════════════════════════════════════
  ('IEAC', $d$ETF obbligazionario — iShares Core € Corp Bond UCITS ETF Dist (ISIN IE00B3F81R35). Contiene qualche migliaio di obbligazioni SOCIETARIE in euro di qualità investment grade (da BBB− in su): banche, assicurazioni, utility e industriali, europei e non, purché emettano in euro. Scadenze di ogni durata, con una vita media attorno ai cinque anni. A DISTRIBUZIONE: paga le cedole sul conto invece di reinvestirle. Due rischi distinti: quello di tasso (se i rendimenti salgono il prezzo scende) e quello di credito degli emittenti, che qui è diversificato su migliaia di nomi.$d$),

  ('XGLE', $d$ETF obbligazionario — Xtrackers II Eurozone Government Bond UCITS ETF 1C (ISIN LU0290355717, Xetra). Contiene i TITOLI DI STATO dei paesi dell'area euro — Italia, Francia, Germania, Spagna e gli altri — pesati per debito in circolazione, con scadenze da un anno in su e quindi una vita media lunga (attorno ai sette anni). Ad accumulazione («1C»): le cedole restano dentro. ⚠️ Nessun rischio di cambio, ma rischio di TASSO pieno: con una durata così, un punto di rialzo dei rendimenti si sente parecchio sul prezzo. La componente italiana porta con sé lo spread.$d$),

  ('BTP10', $d$ETF obbligazionario — Amundi Italy BTP 10Y UCITS ETF (ISIN LU1598691217, ex Lyxor EuroMTS 10Y Italy BTP, su ETFplus). Contiene SOLO BTP italiani a tasso fisso con vita residua intorno ai dieci anni: il fondo li ricompra man mano che invecchiano, così l'esposizione resta ferma sui dieci anni invece di accorciarsi come farebbe un singolo titolo tenuto fino a scadenza. Replica fisica, ad accumulazione. ⚠️ Concentrato su un solo emittente sovrano: qui non c'è diversificazione di credito, c'è l'Italia — e la duration lunga rende il prezzo molto sensibile ai tassi e allo spread.$d$),

  ('INFU', $d$ETF obbligazionario particolare — Amundi US 10Y Inflation Expectations UCITS ETF (ISIN LU1390062831, ex Lyxor, su ETFplus). ⚠️ NON È UN FONDO DI TITOLI INDICIZZATI ALL'INFLAZIONE, e confonderlo con quelli è l'errore da evitare: replica il BREAKEVEN a dieci anni, cioè una posizione lunga sui TIPS americani e contemporaneamente CORTA sui Treasury nominali di pari scadenza. Le due gambe si annullano quasi del tutto sul rischio di tasso — la duration risultante è vicina a zero — e quel che resta è la sola ASPETTATIVA DI INFLAZIONE americana: sale se il mercato si aspetta più inflazione, scende se se ne aspetta meno, indipendentemente dal fatto che i tassi salgano o scendano. È una scommessa direzionale su una variabile, non un cuscinetto obbligazionario. Esposto al cambio euro/dollaro.$d$),

  -- ══ Titoli di Stato italiani ═══════════════════════════════════════════
  ('BTPI-20280314', $d$BTP Italia con scadenza 14 marzo 2028, cedola reale 2,00 % (ISIN IT0005532723, emesso nel marzo 2023, mercato MOT). Titolo di Stato italiano INDICIZZATO ALL'INFLAZIONE ITALIANA (indice FOI senza tabacchi). Funziona così: ogni semestre il capitale viene rivalutato per l'inflazione maturata, la cedola del 2 % annuo si calcola sul capitale già rivalutato, e la rivalutazione stessa viene PAGATA SUBITO insieme alla cedola — non aspetta la scadenza come nei BTP€i legati all'inflazione europea. A scadenza il capitale torna a 100 anche in caso di deflazione. Chi lo ha comprato all'emissione e lo tiene fino alla fine incassa il premio fedeltà. Tassazione agevolata al 12,5 % e fuori dall'asse imponibile di successione, come tutti i titoli di Stato.$d$),

  ('BTPI-20300628', $d$BTP Italia con scadenza 28 giugno 2030, cedola reale 1,60 % (ISIN IT0005497000, emesso nel giugno 2022, mercato MOT). Stesso meccanismo dell'altro BTP Italia in portafoglio: capitale rivalutato semestralmente sull'inflazione italiana (FOI senza tabacchi), cedola dell'1,60 % annuo calcolata sul capitale rivalutato, rivalutazione pagata subito e non a scadenza, rimborso alla pari garantito anche se i prezzi scendono, premio fedeltà a chi lo tiene dall'emissione alla fine. Due anni e mezzo più lungo del 2028, quindi un po' più sensibile ai tassi. Tassazione agevolata al 12,5 %.$d$),

  ('BTP-GREEN-2045', $d$BTP Green con scadenza 30 aprile 2045, cedola fissa 1,50 % annua pagata in due rate semestrali (ISIN IT0005438004, emesso nel marzo 2021 — il primo titolo di Stato verde italiano, mercato MOT). «Green» dice a cosa servono i soldi, non come funziona il titolo: il Tesoro si impegna a destinare la raccolta a spese ambientali (rinnovabili, efficienza, trasporti, tutela del territorio) e a rendicontarle ogni anno. Per il resto è un normalissimo BTP a tasso fisso, senza alcuna indicizzazione. ⚠️ SCADENZA LUNGHISSIMA e cedola bassa: è il titolo più sensibile ai tassi di tutto il portafoglio — a vent'anni di distanza dal rimborso, ogni movimento dei rendimenti si scarica sul prezzo in modo amplificato. Tassazione agevolata al 12,5 %.$d$),

  -- ══ Criptovalute ═══════════════════════════════════════════════════════
  ('BTC', $d$Bitcoin, la prima criptovaluta (2009, dal progetto firmato Satoshi Nakamoto). Rete pubblica senza nessun ente che la emetta o la garantisca, tenuta in piedi dal mining in proof-of-work; l'offerta è LIMITATA A 21 MILIONI di unità e la quantità di nuovi bitcoin si dimezza ogni quattro anni circa (halving). Non genera cedole, dividendi né flussi di cassa: il prezzo è solo quello che qualcun altro è disposto a pagare, e le oscillazioni di decine di punti percentuali in poche settimane sono la norma, non l'eccezione.$d$),

  ('ETH', $d$Ether, la moneta della rete Ethereum (2015). Ethereum è una blockchain PROGRAMMABILE: oltre a trasferire valore esegue contratti automatici, ed è la base su cui girano la maggior parte delle applicazioni decentralizzate, degli stablecoin e degli NFT. Da settembre 2022 («the Merge») funziona in proof-of-stake, quindi non si mina più: si mette ETH in staking per validare i blocchi e se ne ricava un rendimento. ETH serve a pagare le commissioni di rete («gas»). Nessun ente emittente, nessun flusso di cassa garantito, volatilità molto alta.$d$),

  ('SOL', $d$Solana (SOL), blockchain di primo livello nata nel 2020 e pensata per la velocità: molte transazioni al secondo e commissioni di frazioni di centesimo, ottenute con un consenso proof-of-stake affiancato dalla «proof of history» — una marcatura temporale che evita ai validatori di doversi mettere d'accordo sull'ordine dei blocchi. È la rete su cui gira gran parte dello scambio decentralizzato e dei token nati come meme. ⚠️ Quella velocità si paga in nodi più costosi e quindi in una rete più concentrata; in passato ha avuto fermi totali di alcune ore. Volatilità molto alta.$d$),

  ('ADA', $d$Cardano (ADA), blockchain di primo livello proof-of-stake fondata nel 2017 da uno dei cofondatori di Ethereum. ⚠️ Nulla a che vedere con la persona: è il nome di una criptovaluta, preso da Ada Lovelace. Si distingue per il metodo di sviluppo — ogni cambiamento passa da studi accademici sottoposti a revisione paritaria prima di essere scritto nel codice — che l'ha resa solidissima sulla carta e molto lenta nei fatti: le funzioni sono arrivate anni dopo quelle delle reti concorrenti, e sopra Cardano gira molto meno di quanto la sua capitalizzazione lascerebbe pensare. Lo staking è nativo e non blocca le monete.$d$),

  ('AVAX', $d$Avalanche (AVAX), blockchain di primo livello proof-of-stake del 2020. La rete principale è divisa in TRE CATENE con compiti diversi — una per gli scambi, una per i contratti (compatibile con gli strumenti di Ethereum), una per il coordinamento dei validatori — e chiunque può creare una «subnet», cioè una catena propria con regole e valuta proprie ancorata ad Avalanche: è la funzione con cui si presenta alle aziende. AVAX paga le commissioni, si mette in staking, e viene bruciato a ogni transazione. Volatilità molto alta.$d$),

  ('POL', $d$POL (Polygon Ecosystem Token), la moneta della rete Polygon. ⚠️ È IL SEGUITO DI MATIC, non un token nuovo: nel 2024 Polygon ha convertito MATIC in POL uno a uno, e chi aveva MATIC si è ritrovato POL. Polygon è un secondo livello di Ethereum: raccoglie le transazioni fuori dalla catena principale e ve le deposita compresse, così le commissioni scendono da euro a centesimi restando ancorati alla sicurezza di Ethereum. POL serve a pagare le commissioni e a mettersi in staking per validare più catene dell'ecosistema con lo stesso capitale. Volatilità molto alta.$d$),

  ('NEAR', $d$NEAR Protocol (NEAR), blockchain di primo livello proof-of-stake del 2020. La sua idea è lo SHARDING («Nightshade»): la rete si divide in tronconi che lavorano in parallelo, così la capacità cresce aggiungendo tronconi invece di ingrossare ogni nodo. L'altra scelta riconoscibile sono gli indirizzi leggibili — nomi al posto delle stringhe esadecimali — pensati per rendere l'uso meno ostico. NEAR paga le commissioni e si mette in staking. Progetto molto più piccolo di Ethereum e Solana, quindi con meno applicazioni sopra e volatilità ancora maggiore.$d$),

  ('DOGE', $d$Dogecoin (DOGE), nata nel 2013 COME PARODIA delle criptovalute — il nome e l'emblema vengono da un meme — e diventata poi una delle più scambiate. Tecnicamente è una derivazione di Litecoin: proof-of-work, blocchi rapidi, commissioni bassissime. ⚠️ L'OFFERTA È ILLIMITATA: si aggiungono circa 5 miliardi di DOGE all'anno per sempre, quindi non c'è nessuna scarsità programmata come in Bitcoin. Non ha né un ente emittente, né flussi di cassa, né uno sviluppo attivo paragonabile alle altre: il prezzo dipende quasi solo dal clima e dai messaggi di qualche personaggio pubblico. È la posizione più speculativa dell'elenco.$d$)

    ) AS d(simbolo, testo)
   WHERE p.user_id = v_user
     AND p.symbol  = d.simbolo
     AND p.description IS NULL;   -- una descrizione scritta a mano vince su questa

  GET DIAGNOSTICS v_scritte = ROW_COUNT;
  RAISE NOTICE 'fnz_products: scritte % descrizioni.', v_scritte;
END $$;
