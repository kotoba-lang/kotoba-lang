"""Apply the reviewed Japanese correction to the historical legal reference."""
JA_CORRECTIONS = {
    '公開オペレーターと公開連絡先メールボックス。指定されたものではない': '公開運営者と公開連絡先を示すものであり、',
    '商取引法表。': '特定商取引法に基づく表記ではありません。',
    'セキュリティレポートは GitHub のプライベート脆弱性報告を使用します。': '脆弱性の報告には GitHub の非公開報告機能を利用します。',
    'このページは住所、電話番号を公開しません、': 'このページでは、所在地、電話番号、',
    'または会社登録番号。': '法人登記番号を公開していません。',
}
def legal_reference(html, tag):
    if tag != 'ja': return html
    for old, new in JA_CORRECTIONS.items():
        if html.count(old) != 1: raise ValueError(('Japanese reference drift', old))
        html = html.replace(old, new, 1)
    return html
